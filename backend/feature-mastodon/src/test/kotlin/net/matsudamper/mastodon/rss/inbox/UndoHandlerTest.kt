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
import net.matsudamper.mastodon.rss.favourite.FavouriteStore.ReceivedFavourite
import net.matsudamper.mastodon.rss.json.AppJson

class UndoHandlerTest {
    private val now = Instant.parse("2026-08-10T00:00:00Z")

    private val notePublicId = PublicNoteId("note1")

    private val noteUrl = "https://${TestLocalActor.DOMAIN}/notes/${notePublicId.value}"

    private val followUri = "https://remote.example/activities/1"

    private val likeUri = "https://remote.example/likes/1"

    private fun followers(): FakeFollowerStore = FakeFollowerStore().apply {
        record(
            username = TestLocalActor.USERNAME,
            follower = RemoteActor(
                actorId = TestRemoteActor.ACTOR_ID,
                inbox = TestRemoteActor.INBOX,
                sharedInbox = null,
                publicKeyPem = "pem",
                profile = TestRemoteActor.noProfile,
            ),
            followActivityUri = followUri,
            receivedAt = now,
            acceptBody = "{}",
        )
        markAccepted(TestLocalActor.USERNAME, TestRemoteActor.ACTOR_ID)
    }

    private fun favourites(): FakeFavouriteStore = FakeFavouriteStore().apply {
        add(
            ReceivedFavourite(
                notePublicId = notePublicId,
                actor = TestRemoteActor.actor,
                activityUri = likeUri,
                receivedAt = now,
            ),
        )
    }

    private suspend fun handle(
        json: String,
        followers: FakeFollowerStore,
        favourites: FakeFavouriteStore,
    ) {
        val rawActivityJson = AppJson.parseToJsonElement(json) as JsonObject
        UndoHandler(
            favourites = UndoFavouriteHandler(domain = TestLocalActor.DOMAIN, favourites = favourites),
            follows = UndoFollowHandler(followers),
        ).handle(
            recipient = TestLocalActor.urls,
            verifiedSignerActorId = TestRemoteActor.ACTOR_ID,
            activity = AppJson.decodeFromJsonElement(InboxActivity.serializer(), rawActivityJson),
            rawActivityJson = rawActivityJson,
        )
    }

    @Test
    fun `object に Like が埋まっていればお気に入りを取り消す`() = runBlocking {
        val followers = followers()
        val favourites = favourites()

        handle(
            """
            {"id":"https://remote.example/undo/1","type":"Undo","actor":"${TestRemoteActor.ACTOR_ID}",
             "object":{"id":"$likeUri","type":"Like","actor":"${TestRemoteActor.ACTOR_ID}",
                       "object":"$noteUrl"}}
            """.trimIndent(),
            followers = followers,
            favourites = favourites,
        )

        assertTrue(favourites.rows.isEmpty())
        // フォローは巻き込まれない
        assertEquals(1, followers.rows.size)
    }

    @Test
    fun `元のアクティビティの id が無ければ投稿で取り消す`() = runBlocking {
        val favourites = favourites()

        handle(
            """
            {"id":"https://remote.example/undo/1","type":"Undo","actor":"${TestRemoteActor.ACTOR_ID}",
             "object":{"type":"Like","actor":"${TestRemoteActor.ACTOR_ID}","object":"$noteUrl"}}
            """.trimIndent(),
            followers = followers(),
            favourites = favourites,
        )

        assertTrue(favourites.rows.isEmpty())
    }

    @Test
    fun `id だけの Undo は記録しているお気に入りに当たれば取り消す`() = runBlocking {
        val followers = followers()
        val favourites = favourites()

        handle(
            """
            {"id":"https://remote.example/undo/1","type":"Undo",
             "actor":"${TestRemoteActor.ACTOR_ID}","object":"$likeUri"}
            """.trimIndent(),
            followers = followers,
            favourites = favourites,
        )

        assertTrue(favourites.rows.isEmpty())
        assertEquals(1, followers.rows.size)
    }

    @Test
    fun `id だけの Undo がお気に入りに当たらなければフォローを解除する`() = runBlocking {
        val followers = followers()
        val favourites = favourites()

        handle(
            """
            {"id":"https://remote.example/undo/1","type":"Undo",
             "actor":"${TestRemoteActor.ACTOR_ID}","object":"$followUri"}
            """.trimIndent(),
            followers = followers,
            favourites = favourites,
        )

        assertTrue(followers.rows.isEmpty())
        assertEquals(1, favourites.rows.size)
    }

    @Test
    fun `object に Follow が埋まっていればフォローを解除する`() = runBlocking {
        val followers = followers()
        val favourites = favourites()

        handle(
            """
            {"id":"https://remote.example/undo/1","type":"Undo","actor":"${TestRemoteActor.ACTOR_ID}",
             "object":{"id":"$followUri","type":"Follow","actor":"${TestRemoteActor.ACTOR_ID}",
                       "object":"${TestLocalActor.urls.actorId}"}}
            """.trimIndent(),
            followers = followers,
            favourites = favourites,
        )

        assertTrue(followers.rows.isEmpty())
        assertEquals(1, favourites.rows.size)
    }
}
