package net.matsudamper.mastodon.rss.inbox

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import net.matsudamper.mastodon.rss.FakeFavouriteStore
import net.matsudamper.mastodon.rss.FakeNoteStore
import net.matsudamper.mastodon.rss.TestLocalActor
import net.matsudamper.mastodon.rss.TestRemoteActor
import net.matsudamper.mastodon.rss.TestRemoteActors
import net.matsudamper.mastodon.rss.activity.InboxActivity
import net.matsudamper.mastodon.rss.entity.PublicNoteId
import net.matsudamper.mastodon.rss.json.AppJson
import net.matsudamper.mastodon.rss.note.StoredNote

class FavouriteHandlerTest {
    private val recipient = TestLocalActor.urls

    private val notePublicId = PublicNoteId("note1")

    private val noteUrl = "https://${TestLocalActor.DOMAIN}/notes/${notePublicId.value}"

    private fun notes(username: String): FakeNoteStore = FakeNoteStore().apply {
        add(
            StoredNote(
                publicId = notePublicId,
                username = username,
                contentHtml = "<p>本文</p>",
                publishedAt = Instant.parse("2026-08-10T00:00:00Z"),
            ),
        )
    }

    private suspend fun handle(
        json: String,
        notes: FakeNoteStore,
        remoteActors: TestRemoteActors,
    ): FakeFavouriteStore {
        val favourites = FakeFavouriteStore()
        val rawActivityJson = AppJson.parseToJsonElement(json) as JsonObject
        FavouriteHandler(
            domain = TestLocalActor.DOMAIN,
            remoteActors = remoteActors,
            notes = notes,
            favourites = favourites,
        ).handle(
            recipient = recipient,
            verifiedSignerActorId = TestRemoteActor.ACTOR_ID,
            activity = AppJson.decodeFromJsonElement(InboxActivity.serializer(), rawActivityJson),
            rawActivityJson = rawActivityJson,
        )
        return favourites
    }

    private suspend fun handle(json: String): FakeFavouriteStore = handle(
        json = json,
        notes = notes(username = TestLocalActor.USERNAME),
        remoteActors = TestRemoteActors(actors = mapOf(TestRemoteActor.ACTOR_ID to TestRemoteActor.actor)),
    )

    @Test
    fun `content の無い Like をお気に入りとして記録する`() = runBlocking {
        val favourites = handle(
            """
            {"id":"https://remote.example/likes/1","type":"Like",
             "actor":"${TestRemoteActor.ACTOR_ID}","object":"$noteUrl"}
            """.trimIndent(),
        )

        val recorded = favourites.rows.single()
        assertEquals(notePublicId, recorded.notePublicId)
        assertEquals(TestRemoteActor.ACTOR_ID, recorded.actor.actorId)
        assertEquals("https://remote.example/likes/1", recorded.activityUri)
    }

    @Test
    fun `絵文字付きの Like もお気に入りとして記録する`() = runBlocking {
        val favourites = handle(
            """
            {"id":"https://remote.example/likes/1","type":"Like",
             "actor":"${TestRemoteActor.ACTOR_ID}","object":"$noteUrl","content":"👍"}
            """.trimIndent(),
        )

        assertEquals(1, favourites.rows.size)
    }

    @Test
    fun `別のアカウントの投稿へのお気に入りは記録しない`() = runBlocking {
        val favourites = handle(
            json = """
            {"id":"https://remote.example/likes/1","type":"Like",
             "actor":"${TestRemoteActor.ACTOR_ID}","object":"$noteUrl"}
            """.trimIndent(),
            notes = notes(username = TestLocalActor.STORED_USERNAME),
            remoteActors = TestRemoteActors(actors = mapOf(TestRemoteActor.ACTOR_ID to TestRemoteActor.actor)),
        )

        assertTrue(favourites.rows.isEmpty())
    }

    @Test
    fun `よそのサーバーの投稿へのお気に入りは記録しない`() = runBlocking {
        val favourites = handle(
            """
            {"id":"https://remote.example/likes/1","type":"Like",
             "actor":"${TestRemoteActor.ACTOR_ID}","object":"https://other.example/notes/1"}
            """.trimIndent(),
        )

        assertTrue(favourites.rows.isEmpty())
    }

    @Test
    fun `id の無い Like は記録しない`() = runBlocking {
        val favourites = handle(
            """
            {"type":"Like","actor":"${TestRemoteActor.ACTOR_ID}","object":"$noteUrl"}
            """.trimIndent(),
        )

        assertTrue(favourites.rows.isEmpty())
    }

    @Test
    fun `押した相手のアクター文書を引けなければ記録しない`() = runBlocking {
        val favourites = handle(
            json = """
            {"id":"https://remote.example/likes/1","type":"Like",
             "actor":"${TestRemoteActor.ACTOR_ID}","object":"$noteUrl"}
            """.trimIndent(),
            notes = notes(username = TestLocalActor.USERNAME),
            remoteActors = TestRemoteActors(),
        )

        assertTrue(favourites.rows.isEmpty())
    }
}
