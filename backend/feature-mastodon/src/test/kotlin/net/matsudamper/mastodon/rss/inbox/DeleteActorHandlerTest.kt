package net.matsudamper.mastodon.rss.inbox

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import net.matsudamper.mastodon.rss.FakeFavouriteStore
import net.matsudamper.mastodon.rss.FakeFollowerStore
import net.matsudamper.mastodon.rss.TestLocalActor
import net.matsudamper.mastodon.rss.TestRemoteActor
import net.matsudamper.mastodon.rss.activity.InboxActivity
import net.matsudamper.mastodon.rss.actor.RemoteActor
import net.matsudamper.mastodon.rss.entity.PublicNoteId
import net.matsudamper.mastodon.rss.favourite.ReceivedFavourite
import net.matsudamper.mastodon.rss.json.AppJson

// アカウントが消えたフォロワーの掃除。
// Delete は投稿の削除にも使われるので、object が送り主自身かどうかで見分ける。
class DeleteActorHandlerTest {
    private val now = Instant.parse("2026-08-10T00:00:00Z")

    /**
     * 同じ相手が admin と feed1 の両方をフォローしている状態
     */
    private fun followers(): FakeFollowerStore = FakeFollowerStore().apply {
        listOf(TestLocalActor.USERNAME, TestLocalActor.STORED_USERNAME).forEachIndexed { index, username ->
            record(
                username = username,
                follower = RemoteActor(
                    actorId = TestRemoteActor.ACTOR_ID,
                    inbox = TestRemoteActor.INBOX,
                    sharedInbox = null,
                    publicKeyPem = "pem",
                    profile = TestRemoteActor.noProfile,
                ),
                followActivityUri = "https://remote.example/activities/$index",
                receivedAt = now,
                acceptBody = "{}",
            )
            markAccepted(username, TestRemoteActor.ACTOR_ID)
        }
    }

    private fun favourites(): FakeFavouriteStore = FakeFavouriteStore().apply {
        add(
            ReceivedFavourite(
                notePublicId = PublicNoteId("note1"),
                actor = TestRemoteActor.actor,
                activityUri = "https://remote.example/likes/1",
                receivedAt = now,
            ),
        )
    }

    private suspend fun handle(
        store: FakeFollowerStore,
        json: String,
        favourites: FakeFavouriteStore = FakeFavouriteStore(),
    ) {
        val rawActivityJson = AppJson.parseToJsonElement(json) as JsonObject
        DeleteActorHandler(followers = store, favourites = favourites).handle(
            recipient = TestLocalActor.urls,
            verifiedSignerActorId = TestRemoteActor.ACTOR_ID,
            activity = AppJson.decodeFromJsonElement(InboxActivity.serializer(), rawActivityJson),
            rawActivityJson = rawActivityJson,
        )
    }

    @Test
    fun `自分自身の Delete でフォロワーから外す`() = runBlocking {
        val store = followers()
        val favourites = favourites()

        handle(
            store,
            """
            {"id":"https://remote.example/activities/9","type":"Delete",
             "actor":"${TestRemoteActor.ACTOR_ID}","object":"${TestRemoteActor.ACTOR_ID}"}
            """.trimIndent(),
            favourites = favourites,
        )

        assertTrue(favourites.rows.isEmpty())

        // 宛先のアカウントだけでなく、この相手のフォローが全部消える。
        // 残すと消えた相手に送り続けることになる
        assertEquals(0, store.count(TestLocalActor.USERNAME))
        assertEquals(0, store.count(TestLocalActor.STORED_USERNAME))
    }

    @Test
    fun `投稿の Delete では何もしない`() = runBlocking {
        val store = followers()

        handle(
            store,
            """
            {"id":"https://remote.example/activities/9","type":"Delete",
             "actor":"${TestRemoteActor.ACTOR_ID}","object":"https://remote.example/notes/1"}
            """.trimIndent(),
        )

        assertEquals(1, store.count(TestLocalActor.USERNAME))
        assertEquals(1, store.count(TestLocalActor.STORED_USERNAME))
    }
}
