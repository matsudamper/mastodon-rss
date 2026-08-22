package net.matsudamper.mastodon.rss.inbox

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import net.matsudamper.mastodon.rss.FakeFollowerStore
import net.matsudamper.mastodon.rss.TestLocalActor
import net.matsudamper.mastodon.rss.TestRemoteActor
import net.matsudamper.mastodon.rss.activitypub.InboxActivity
import net.matsudamper.mastodon.rss.actor.RemoteActor
import net.matsudamper.mastodon.rss.json.AppJson

// Undo でフォローを解除する。
// object に Follow が丸ごと入っている形と、id だけの形の両方が届く。
class UndoFollowHandlerTest {
    private val recipient = TestLocalActor.urls

    private val followUri = "https://remote.example/activities/1"

    private val now = Instant.parse("2026-08-10T00:00:00Z")

    private fun followers(): FakeFollowerStore = FakeFollowerStore().apply {
        record(
            username = TestLocalActor.USERNAME,
            follower = RemoteActor(
                actorId = TestRemoteActor.ACTOR_ID,
                inbox = TestRemoteActor.INBOX,
                sharedInbox = null,
                publicKeyPem = "pem",
            ),
            followActivityUri = followUri,
            receivedAt = now,
        )
        markAccepted(TestLocalActor.USERNAME, TestRemoteActor.ACTOR_ID, now)
    }

    private suspend fun handle(
        store: FakeFollowerStore,
        json: String,
        signer: String = TestRemoteActor.ACTOR_ID,
    ) {
        val raw = AppJson.parseToJsonElement(json) as JsonObject
        UndoFollowHandler(store).handle(
            recipient = recipient,
            signer = signer,
            activity = AppJson.decodeFromJsonElement(InboxActivity.serializer(), raw),
            raw = raw,
        )
    }

    @Test
    fun `object に Follow が埋まっていれば解除する`() = runBlocking {
        val store = followers()

        handle(
            store,
            """
            {"id":"https://remote.example/activities/2","type":"Undo",
             "actor":"${TestRemoteActor.ACTOR_ID}",
             "object":{"id":"$followUri","type":"Follow",
                       "actor":"${TestRemoteActor.ACTOR_ID}","object":"${recipient.actorId}"}}
            """.trimIndent(),
        )

        assertEquals(0, store.count(TestLocalActor.USERNAME))
    }

    @Test
    fun `object が Follow の id だけでも解除する`() = runBlocking {
        val store = followers()

        handle(
            store,
            """
            {"id":"https://remote.example/activities/2","type":"Undo",
             "actor":"${TestRemoteActor.ACTOR_ID}","object":"$followUri"}
            """.trimIndent(),
        )

        assertEquals(0, store.count(TestLocalActor.USERNAME))
    }

    @Test
    fun `記録していない id の Undo では解除しない`() = runBlocking {
        val store = followers()

        // Undo{Like} などが id だけで来た場合。何のアクティビティだったのかは
        // こちらの記録と突き合わせるしか判断材料が無い
        handle(
            store,
            """
            {"id":"https://remote.example/activities/2","type":"Undo",
             "actor":"${TestRemoteActor.ACTOR_ID}","object":"https://remote.example/likes/9"}
            """.trimIndent(),
        )

        assertEquals(1, store.count(TestLocalActor.USERNAME))
    }

    @Test
    fun `対象が Follow でなければ解除しない`() = runBlocking {
        val store = followers()

        handle(
            store,
            """
            {"id":"https://remote.example/activities/2","type":"Undo",
             "actor":"${TestRemoteActor.ACTOR_ID}",
             "object":{"id":"https://remote.example/likes/9","type":"Like",
                       "actor":"${TestRemoteActor.ACTOR_ID}","object":"${recipient.actorId}"}}
            """.trimIndent(),
        )

        assertEquals(1, store.count(TestLocalActor.USERNAME))
    }

    @Test
    fun `他人のフォローは解除できない`() = runBlocking {
        val store = followers()

        // 署名の持ち主とは別の相手のフォローを消そうとする形。
        // object の中身ではなく署名者で絞るので消えない
        handle(
            store,
            """
            {"id":"https://remote.example/activities/2","type":"Undo",
             "actor":"https://remote.example/users/mallory",
             "object":{"id":"$followUri","type":"Follow",
                       "actor":"${TestRemoteActor.ACTOR_ID}","object":"${recipient.actorId}"}}
            """.trimIndent(),
            signer = "https://remote.example/users/mallory",
        )

        assertEquals(1, store.count(TestLocalActor.USERNAME))
    }
}
