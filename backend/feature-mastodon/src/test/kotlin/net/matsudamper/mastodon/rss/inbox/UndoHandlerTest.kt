package net.matsudamper.mastodon.rss.inbox

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import net.matsudamper.mastodon.rss.FakeEarlyUndoneLikes
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
                receivedAt = now,
            ),
        )
    }

    private suspend fun handle(
        json: String,
        followers: FakeFollowerStore,
        favourites: FakeFavouriteStore,
        earlyUndoneLikes: FakeEarlyUndoneLikes = FakeEarlyUndoneLikes(),
    ) {
        val rawActivityJson = AppJson.parseToJsonElement(json) as JsonObject
        UndoHandler(
            favourites = UndoFavouriteHandler(
                domain = TestLocalActor.DOMAIN,
                favourites = favourites,
                earlyUndoneLikes = earlyUndoneLikes,
            ),
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
    fun `id だけの Undo ではお気に入りを取り消さず、後から届く同じ id の Like に備える`() = runBlocking {
        val followers = followers()
        val favourites = favourites()
        val earlyUndoneLikes = FakeEarlyUndoneLikes()

        handle(
            """
            {"id":"https://remote.example/undo/1","type":"Undo",
             "actor":"${TestRemoteActor.ACTOR_ID}","object":"$likeUri"}
            """.trimIndent(),
            followers = followers,
            favourites = favourites,
            earlyUndoneLikes = earlyUndoneLikes,
        )

        // Mastodon と同じく、Like の id は記録していないので引き当てられない
        assertEquals(1, favourites.rows.size)
        assertEquals(1, followers.rows.size)
        assertTrue(earlyUndoneLikes.isRemembered(actorUri = TestRemoteActor.ACTOR_ID, activityUri = likeUri, now = Instant.now()))
    }

    @Test
    fun `記録に無い Like の取り消しは、後から届く同じ id の Like に備える`() = runBlocking {
        val earlyUndoneLikes = FakeEarlyUndoneLikes()

        handle(
            """
            {"id":"https://remote.example/undo/1","type":"Undo","actor":"${TestRemoteActor.ACTOR_ID}",
             "object":{"id":"$likeUri","type":"Like","actor":"${TestRemoteActor.ACTOR_ID}",
                       "object":"$noteUrl"}}
            """.trimIndent(),
            followers = followers(),
            favourites = FakeFavouriteStore(),
            earlyUndoneLikes = earlyUndoneLikes,
        )

        assertTrue(earlyUndoneLikes.isRemembered(actorUri = TestRemoteActor.ACTOR_ID, activityUri = likeUri, now = Instant.now()))
    }

    @Test
    fun `フォローを解除した id だけの Undo は Like の取り消しとして覚えない`() = runBlocking {
        val earlyUndoneLikes = FakeEarlyUndoneLikes()

        handle(
            """
            {"id":"https://remote.example/undo/1","type":"Undo",
             "actor":"${TestRemoteActor.ACTOR_ID}","object":"$followUri"}
            """.trimIndent(),
            followers = followers(),
            favourites = favourites(),
            earlyUndoneLikes = earlyUndoneLikes,
        )

        assertFalse(earlyUndoneLikes.isRemembered(actorUri = TestRemoteActor.ACTOR_ID, activityUri = followUri, now = Instant.now()))
    }

    @Test
    fun `id だけの Undo はフォローの解除として扱う`() = runBlocking {
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
