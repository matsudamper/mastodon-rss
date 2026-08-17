package net.matsudamper.mastodon.rss.inbox

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import net.matsudamper.mastodon.rss.FakeFollowerStore
import net.matsudamper.mastodon.rss.TestDelivery
import net.matsudamper.mastodon.rss.TestLocalActor
import net.matsudamper.mastodon.rss.TestRemoteActor
import net.matsudamper.mastodon.rss.TestRemoteActors
import net.matsudamper.mastodon.rss.activitypub.InboxActivity
import net.matsudamper.mastodon.rss.delivery.DeliveryResult
import net.matsudamper.mastodon.rss.json.AppJson

// Follow を受けてフォロワーとして記録し、Accept を返すところまで。
// 記録が先で Accept が後、という順番がこのハンドラの肝になる。
class FollowHandlerTest {
    private val recipient = TestLocalActor.urls

    private fun followJson(
        id: String? = "https://remote.example/activities/1",
        target: String = recipient.actorId,
    ): String {
        val idField = if (id == null) "" else """"id":"$id","""
        return """{$idField"type":"Follow","actor":"${TestRemoteActor.ACTOR_ID}","object":"$target"}"""
    }

    private suspend fun handle(
        handler: FollowHandler,
        json: String,
    ) {
        val raw = AppJson.parseToJsonElement(json) as JsonObject
        handler.handle(
            recipient = recipient,
            signer = TestRemoteActor.ACTOR_ID,
            activity = AppJson.decodeFromJsonElement(InboxActivity.serializer(), raw),
            raw = raw,
        )
    }

    @Test
    fun `記録してから Accept を返す`() = runBlocking {
        val followers = FakeFollowerStore()
        val delivery = TestDelivery()

        handle(FollowHandler(TestRemoteActor.remoteActors(), delivery, followers), followJson())

        val row = followers.rows.single()
        assertEquals(TestLocalActor.USERNAME, row.username)
        assertEquals(TestRemoteActor.ACTOR_ID, row.followerActorUri)
        // 記録する inbox と鍵は、Accept を送るのに引いたアクター文書から取る
        assertEquals(TestRemoteActor.INBOX, row.inbox)
        assertTrue(row.publicKeyPem.startsWith("-----BEGIN PUBLIC KEY-----"))
        assertEquals("https://remote.example/activities/1", row.followActivityUri)
        assertTrue(row.accepted, "Accept を返せたのにフォロワーとして数えていない")

        assertEquals(listOf(TestRemoteActor.INBOX), delivery.delivered.map { it.inbox })
    }

    @Test
    fun `Accept を返せなければフォロワーに数えない`() = runBlocking {
        val followers = FakeFollowerStore()

        handle(
            FollowHandler(
                TestRemoteActor.remoteActors(),
                TestDelivery(result = DeliveryResult.Failed("届かない")),
                followers,
            ),
            followJson(),
        )

        // 記録自体は残す。相手が送り直してきたときに二重に行を作らないため
        assertFalse(followers.rows.single().accepted)
        assertEquals(0, followers.count(TestLocalActor.USERNAME))
    }

    @Test
    fun `記録できなければ Accept を返さない`() = runBlocking {
        val delivery = TestDelivery()

        handle(
            FollowHandler(TestRemoteActor.remoteActors(), delivery, FakeFollowerStore(failOnRecord = true)),
            followJson(),
        )

        // 送り先が残らないのに相手だけがフォローできたつもりになる状態を作らない
        assertEquals(emptyList(), delivery.delivered)
    }

    @Test
    fun `id の無い Follow は受け付けない`() = runBlocking {
        val followers = FakeFollowerStore()
        val delivery = TestDelivery()

        handle(FollowHandler(TestRemoteActor.remoteActors(), delivery, followers), followJson(id = null))

        // 送り直しと新しい Follow を区別できないので記録も Accept もしない
        assertEquals(emptyList(), followers.rows)
        assertEquals(emptyList(), delivery.delivered)
    }

    @Test
    fun `宛先が違う Follow は記録しない`() = runBlocking {
        val followers = FakeFollowerStore()
        val delivery = TestDelivery()

        handle(
            FollowHandler(TestRemoteActor.remoteActors(), delivery, followers),
            followJson(target = "https://example.com/users/someone-else"),
        )

        assertEquals(emptyList(), followers.rows)
        assertEquals(emptyList(), delivery.delivered)
    }

    @Test
    fun `相手のアクターを引けなければ記録しない`() = runBlocking {
        val followers = FakeFollowerStore()
        val delivery = TestDelivery()

        handle(FollowHandler(TestRemoteActors(), delivery, followers), followJson())

        // inbox も鍵も取れていないので、記録する中身が揃っていない
        assertEquals(emptyList(), followers.rows)
        assertEquals(emptyList(), delivery.delivered)
    }

    @Test
    fun `同じ Follow を二重に受けても行が増えない`() = runBlocking {
        val followers = FakeFollowerStore()
        val delivery = TestDelivery()
        val handler = FollowHandler(TestRemoteActor.remoteActors(), delivery, followers)

        handle(handler, followJson())
        handle(handler, followJson())

        assertEquals(1, followers.rows.size)
        // Accept は送り直す。相手が送り直してきたのは受け取れていないからで、
        // こちらの記録があることは相手には見えない
        assertEquals(2, delivery.delivered.size)
    }
}
