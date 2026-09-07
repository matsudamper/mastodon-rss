package net.matsudamper.mastodon.rss.inbox

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import net.matsudamper.mastodon.rss.FakeFollowerStore
import net.matsudamper.mastodon.rss.FakeNoteStore
import net.matsudamper.mastodon.rss.TestDelivery
import net.matsudamper.mastodon.rss.TestLocalActor
import net.matsudamper.mastodon.rss.TestRemoteActor
import net.matsudamper.mastodon.rss.TestRemoteActors
import net.matsudamper.mastodon.rss.activity.CreateNoteActivity
import net.matsudamper.mastodon.rss.activity.InboxActivity
import net.matsudamper.mastodon.rss.actor.RemoteActor
import net.matsudamper.mastodon.rss.actor.RemoteActors
import net.matsudamper.mastodon.rss.delivery.DeliveryResult
import net.matsudamper.mastodon.rss.entity.PublicNoteId
import net.matsudamper.mastodon.rss.json.AppJson
import net.matsudamper.mastodon.rss.note.FollowBackfillPublisher
import net.matsudamper.mastodon.rss.note.StoredNote

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

    private fun followHandler(
        delivery: TestDelivery,
        followers: FakeFollowerStore,
        notes: FakeNoteStore = FakeNoteStore(),
        remoteActors: RemoteActors = TestRemoteActor.remoteActors(),
    ): FollowHandler = FollowHandler(
        remoteActors = remoteActors,
        delivery = delivery,
        followers = followers,
        backfill = FollowBackfillPublisher(notes = notes, delivery = delivery),
    )

    private fun notesOf(count: Int): FakeNoteStore = FakeNoteStore().apply {
        repeat(count) { index ->
            add(
                StoredNote(
                    publicId = PublicNoteId("note-%02d".format(index)),
                    username = TestLocalActor.USERNAME,
                    contentHtml = "<p>$index</p>",
                    publishedAt = Instant.parse("2026-08-10T00:00:00Z").plusSeconds(index.toLong()),
                ),
            )
        }
    }

    private fun deliveredCreates(delivery: TestDelivery): List<CreateNoteActivity> = delivery.delivered
        .drop(1)
        .map { AppJson.decodeFromString(CreateNoteActivity.serializer(), it.body) }

    @Test
    fun `フォローが成立したら過去の投稿を新しいフォロワーに配る`() = runBlocking {
        val delivery = TestDelivery()
        val notes = notesOf(3)

        handle(
            followHandler(delivery = delivery, followers = FakeFollowerStore(), notes = notes),
            followJson(),
        )

        // 先頭は Accept。過去の投稿は古い順に続く
        assertEquals(listOf(TestRemoteActor.INBOX), delivery.delivered.map { it.inbox }.distinct())
        val creates = deliveredCreates(delivery)
        assertEquals(listOf("<p>0</p>", "<p>1</p>", "<p>2</p>"), creates.map { it.target.content })
    }

    @Test
    fun `再配信した Create は元の投稿と同じ id と日時になる`() = runBlocking {
        val delivery = TestDelivery()
        val notes = notesOf(1)

        handle(
            followHandler(delivery = delivery, followers = FakeFollowerStore(), notes = notes),
            followJson(),
        )

        // 相手はこの id で重複を判断する。作り直すとタイムラインに 2 度並ぶ
        val note = notes.added.single()
        val create = deliveredCreates(delivery).single()
        assertEquals("https://${TestLocalActor.DOMAIN}/notes/${note.publicId.value}#create", create.id.value)
        assertEquals("https://${TestLocalActor.DOMAIN}/notes/${note.publicId.value}", create.target.id.value)
        assertEquals(create.published, create.target.published)
        assertEquals("2026-08-10T00:00:00Z", create.published)
    }

    @Test
    fun `再配信する件数には上限がある`() = runBlocking {
        val delivery = TestDelivery()

        handle(
            followHandler(delivery = delivery, followers = FakeFollowerStore(), notes = notesOf(25)),
            followJson(),
        )

        val creates = deliveredCreates(delivery)
        assertEquals(20, creates.size)
        // 上限を超えるときは新しい方を残す
        assertEquals("<p>24</p>", creates.last().target.content)
        assertEquals("<p>5</p>", creates.first().target.content)
    }

    @Test
    fun `Accept を返せなければ過去の投稿も配らない`() = runBlocking {
        val delivery = TestDelivery(result = DeliveryResult.Failed("届かない"))

        handle(
            followHandler(delivery = delivery, followers = FakeFollowerStore(), notes = notesOf(3)),
            followJson(),
        )

        // フォローが成立していない相手のタイムラインには並ばない
        assertEquals(1, delivery.delivered.size)
    }

    @Test
    fun `既にフォローしている相手には再配信しない`() = runBlocking {
        val followers = FakeFollowerStore()
        val delivery = TestDelivery()
        val notes = notesOf(3)
        val handler = followHandler(delivery = delivery, followers = followers, notes = notes)

        handle(handler, followJson())
        val otherFollowerInbox = "https://other.example/users/bob/inbox"
        followers.record(
            username = TestLocalActor.USERNAME,
            follower = RemoteActor(
                actorId = "https://other.example/users/bob",
                inbox = otherFollowerInbox,
                sharedInbox = null,
                publicKeyPem = "pem",
            ),
            followActivityUri = "https://other.example/activities/1",
            receivedAt = Instant.parse("2026-08-10T00:00:00Z"),
        )
        followers.markAccepted(TestLocalActor.USERNAME, "https://other.example/users/bob", Instant.parse("2026-08-10T00:00:00Z"))

        handle(handler, followJson())

        // 送り先は Follow を送ってきた相手だけ
        assertEquals(listOf(TestRemoteActor.INBOX), delivery.delivered.map { it.inbox }.distinct())
    }

    private suspend fun handle(
        handler: FollowHandler,
        json: String,
    ) {
        val rawActivityJson = AppJson.parseToJsonElement(json) as JsonObject
        handler.handle(
            recipient = recipient,
            verifiedSignerActorId = TestRemoteActor.ACTOR_ID,
            activity = AppJson.decodeFromJsonElement(InboxActivity.serializer(), rawActivityJson),
            rawActivityJson = rawActivityJson,
        )
    }

    @Test
    fun `記録してから Accept を返す`() = runBlocking {
        val followers = FakeFollowerStore()
        val delivery = TestDelivery()

        handle(followHandler(delivery = delivery, followers = followers), followJson())

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
            followHandler(delivery = TestDelivery(result = DeliveryResult.Failed("届かない")), followers = followers),
            followJson(),
        )

        // 記録自体は残す。相手が送り直してきたときに二重に行を作らないため
        assertFalse(followers.rows.single().accepted)
        assertEquals(0, followers.count(TestLocalActor.USERNAME))
    }

    @Test
    fun `Accept の後の記録が一度失敗しても数える`() = runBlocking {
        val followers = FakeFollowerStore(failMarkAcceptedTimes = 1)

        handle(followHandler(delivery = TestDelivery(), followers = followers), followJson())

        // ここで諦めると、相手にはフォロー中と見えるのに投稿が届かない状態が残る
        assertEquals(2, followers.markAcceptedAttempts)
        assertTrue(followers.rows.single().accepted)
    }

    @Test
    fun `Accept の後の記録が false を返しても数える`() = runBlocking {
        val followers = FakeFollowerStore(failMarkAcceptedReturnsFalseTimes = 1)

        handle(followHandler(delivery = TestDelivery(), followers = followers), followJson())

        assertEquals(2, followers.markAcceptedAttempts)
        assertTrue(followers.rows.single().accepted)
    }

    @Test
    fun `記録できなければ Accept を返さない`() = runBlocking {
        val delivery = TestDelivery()

        handle(
            followHandler(delivery = delivery, followers = FakeFollowerStore(failOnRecord = true)),
            followJson(),
        )

        // 送り先が残らないのに相手だけがフォローできたつもりになる状態を作らない
        assertEquals(emptyList(), delivery.delivered)
    }

    @Test
    fun `id の無い Follow は受け付けない`() = runBlocking {
        val followers = FakeFollowerStore()
        val delivery = TestDelivery()

        handle(followHandler(delivery = delivery, followers = followers), followJson(id = null))

        // 送り直しと新しい Follow を区別できないので記録も Accept もしない
        assertEquals(emptyList(), followers.rows)
        assertEquals(emptyList(), delivery.delivered)
    }

    @Test
    fun `宛先が違う Follow は記録しない`() = runBlocking {
        val followers = FakeFollowerStore()
        val delivery = TestDelivery()

        handle(
            followHandler(delivery = delivery, followers = followers),
            followJson(target = "https://example.com/users/someone-else"),
        )

        assertEquals(emptyList(), followers.rows)
        assertEquals(emptyList(), delivery.delivered)
    }

    @Test
    fun `相手のアクターを引けなければ記録しない`() = runBlocking {
        val followers = FakeFollowerStore()
        val delivery = TestDelivery()

        handle(
            followHandler(remoteActors = TestRemoteActors(), delivery = delivery, followers = followers),
            followJson(),
        )

        // inbox も鍵も取れていないので、記録する中身が揃っていない
        assertEquals(emptyList(), followers.rows)
        assertEquals(emptyList(), delivery.delivered)
    }

    @Test
    fun `同じ Follow を二重に受けても行が増えない`() = runBlocking {
        val followers = FakeFollowerStore()
        val delivery = TestDelivery()
        val handler = followHandler(delivery = delivery, followers = followers)

        handle(handler, followJson())
        handle(handler, followJson())

        assertEquals(1, followers.rows.size)
        // Accept は送り直す。相手が送り直してきたのは受け取れていないからで、
        // こちらの記録があることは相手には見えない
        assertEquals(2, delivery.delivered.size)
    }
}
