package net.matsudamper.mastodon.rss.inbox

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import net.matsudamper.mastodon.rss.FakeEarlyUndoneLikes
import net.matsudamper.mastodon.rss.FakeFavouriteStore
import net.matsudamper.mastodon.rss.TestLocalActor
import net.matsudamper.mastodon.rss.TestRemoteActor
import net.matsudamper.mastodon.rss.TestRemoteActors
import net.matsudamper.mastodon.rss.activity.InboxActivity
import net.matsudamper.mastodon.rss.entity.PublicNoteId
import net.matsudamper.mastodon.rss.json.AppJson

class FavouriteHandlerTest {
    private val notePublicId = PublicNoteId("note1")

    private val noteUrl = "https://${TestLocalActor.DOMAIN}/notes/${notePublicId.value}"

    private val knownActor = TestRemoteActors(actors = mapOf(TestRemoteActor.ACTOR_ID to TestRemoteActor.actor))

    private suspend fun handle(
        json: String,
        remoteActors: TestRemoteActors,
        earlyUndoneLikes: FakeEarlyUndoneLikes,
    ): FakeFavouriteStore {
        val favourites = FakeFavouriteStore()
        val rawActivityJson = AppJson.parseToJsonElement(json) as JsonObject
        FavouriteHandler(
            domain = TestLocalActor.DOMAIN,
            remoteActors = remoteActors,
            favourites = favourites,
            earlyUndoneLikes = earlyUndoneLikes,
        ).handle(
            recipient = TestLocalActor.urls,
            verifiedSignerActorId = TestRemoteActor.ACTOR_ID,
            activity = AppJson.decodeFromJsonElement(InboxActivity.serializer(), rawActivityJson),
            rawActivityJson = rawActivityJson,
        )
        return favourites
    }

    private suspend fun handle(json: String): FakeFavouriteStore =
        handle(json = json, remoteActors = knownActor, earlyUndoneLikes = FakeEarlyUndoneLikes())

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
    fun `id の無い Like も記録する`() = runBlocking {
        val favourites = handle(
            """
            {"type":"Like","actor":"${TestRemoteActor.ACTOR_ID}","object":"$noteUrl"}
            """.trimIndent(),
        )

        assertEquals(1, favourites.rows.size)
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
    fun `押した相手のアクター文書を引けなければ記録しない`() = runBlocking {
        val favourites = handle(
            json = """
            {"id":"https://remote.example/likes/1","type":"Like",
             "actor":"${TestRemoteActor.ACTOR_ID}","object":"$noteUrl"}
            """.trimIndent(),
            remoteActors = TestRemoteActors(),
            earlyUndoneLikes = FakeEarlyUndoneLikes(),
        )

        assertTrue(favourites.rows.isEmpty())
    }

    @Test
    fun `先に取り消しが届いた Like は記録しない`() = runBlocking {
        val earlyUndoneLikes = FakeEarlyUndoneLikes().apply {
            remember(actorUri = TestRemoteActor.ACTOR_ID, activityUri = "https://remote.example/likes/1", expiresAt = Instant.MAX)
        }

        val favourites = handle(
            json = """
            {"id":"https://remote.example/likes/1","type":"Like",
             "actor":"${TestRemoteActor.ACTOR_ID}","object":"$noteUrl"}
            """.trimIndent(),
            remoteActors = knownActor,
            earlyUndoneLikes = earlyUndoneLikes,
        )

        assertTrue(favourites.rows.isEmpty())
    }

    @Test
    fun `期限を過ぎた取り消しでは Like を止めない`() = runBlocking {
        val earlyUndoneLikes = FakeEarlyUndoneLikes().apply {
            remember(
                actorUri = TestRemoteActor.ACTOR_ID,
                activityUri = "https://remote.example/likes/1",
                expiresAt = Instant.now().minusSeconds(1),
            )
        }

        val favourites = handle(
            json = """
            {"id":"https://remote.example/likes/1","type":"Like",
             "actor":"${TestRemoteActor.ACTOR_ID}","object":"$noteUrl"}
            """.trimIndent(),
            remoteActors = knownActor,
            earlyUndoneLikes = earlyUndoneLikes,
        )

        assertEquals(1, favourites.rows.size)
    }

    @Test
    fun `別の相手が先に取り消しを送っていても記録する`() = runBlocking {
        val earlyUndoneLikes = FakeEarlyUndoneLikes().apply {
            remember(actorUri = "https://remote.example/users/bob", activityUri = "https://remote.example/likes/1", expiresAt = Instant.MAX)
        }

        val favourites = handle(
            json = """
            {"id":"https://remote.example/likes/1","type":"Like",
             "actor":"${TestRemoteActor.ACTOR_ID}","object":"$noteUrl"}
            """.trimIndent(),
            remoteActors = knownActor,
            earlyUndoneLikes = earlyUndoneLikes,
        )

        assertEquals(1, favourites.rows.size)
    }
}
