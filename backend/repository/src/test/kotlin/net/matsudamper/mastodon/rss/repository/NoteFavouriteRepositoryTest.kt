package net.matsudamper.mastodon.rss.repository

import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.matsudamper.mastodon.rss.repository.NoteFavouriteRepository.NewNoteFavourite
import net.matsudamper.mastodon.rss.shared.PublicNoteId

class NoteFavouriteRepositoryTest {
    private val tempDir: Path = createTempDirectory("mastodon-rss-note-favourite-test")

    private val dbPath: Path = tempDir.resolve("test.db")

    private val now: Instant = Instant.parse("2026-08-10T00:00:00Z")

    private val notePublicId = PublicNoteId("note1")

    private val actorUri = "https://remote.example/users/alice"

    private val otherActorUri = "https://remote.example/users/bob"

    init {
        TestSchema.applyTo(dbPath)
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun <T> withRepositories(block: (Repositories) -> T): T =
        createRepositories(DatabaseConfig(path = dbPath)).use { repositories ->
            repositories.notes.add(
                NewNote(
                    username = "admin",
                    publicId = notePublicId,
                    contentHtml = "<p>本文</p>",
                    publishedAt = now,
                ),
            )
            block(repositories)
        }

    /**
     * 相手ごとに違う鍵にして、取り違えが分かるようにする
     */
    private fun actor(actorUri: String): NewRemoteActor = NewRemoteActor(
        actorUri = actorUri,
        inbox = "$actorUri/inbox",
        sharedInbox = null,
        publicKeyPem = "pem of $actorUri",
        profile = RemoteActorProfile(
            preferredUsername = null,
            displayName = null,
            profileUrl = null,
            iconUrl = null,
        ),
    )

    private fun favourite(actorUri: String): NewNoteFavourite = NewNoteFavourite(
        notePublicId = notePublicId,
        actor = actor(actorUri),
        receivedAt = now,
    )

    @Test
    fun `投稿ごとにお気に入りを数えられる`() {
        withRepositories { repositories ->
            val favourites = repositories.noteFavourites
            favourites.add(favourite(actorUri = actorUri))
            favourites.add(favourite(actorUri = otherActorUri))

            val counted = favourites.countsByNotes(setOf(notePublicId, PublicNoteId("none")))

            assertEquals(mapOf(notePublicId to 2), counted)
        }
    }

    @Test
    fun `同じ相手のお気に入りは増えない`() {
        withRepositories { repositories ->
            val favourites = repositories.noteFavourites
            assertTrue(favourites.add(favourite(actorUri = actorUri)))

            assertFalse(favourites.add(favourite(actorUri = actorUri)))

            assertEquals(mapOf(notePublicId to 1), favourites.countsByNotes(setOf(notePublicId)))
        }
    }

    @Test
    fun `配信していない投稿へのお気に入りは記録しない`() {
        withRepositories { repositories ->
            val added = repositories.noteFavourites.add(
                NewNoteFavourite(
                    notePublicId = PublicNoteId("none"),
                    actor = actor(actorUri),
                    receivedAt = now,
                ),
            )

            assertFalse(added)
        }
    }

    @Test
    fun `取り消しは投稿と押した相手で消せる`() {
        withRepositories { repositories ->
            val favourites = repositories.noteFavourites
            favourites.add(favourite(actorUri = actorUri))

            assertFalse(favourites.removeByNote(notePublicId = notePublicId, actorUri = otherActorUri))
            assertTrue(favourites.removeByNote(notePublicId = notePublicId, actorUri = actorUri))

            assertEquals(mapOf(), favourites.countsByNotes(setOf(notePublicId)))
        }
    }

    @Test
    fun `消えた相手のお気に入りをまとめて消せる`() {
        withRepositories { repositories ->
            val favourites = repositories.noteFavourites
            favourites.add(favourite(actorUri = actorUri))
            favourites.add(favourite(actorUri = otherActorUri))

            assertEquals(1, favourites.removeByActor(actorUri))

            assertEquals(mapOf(notePublicId to 1), favourites.countsByNotes(setOf(notePublicId)))
        }
    }

    @Test
    fun `投稿を消すとお気に入りも消える`() {
        withRepositories { repositories ->
            repositories.noteFavourites.add(favourite(actorUri = actorUri))

            repositories.notes.delete(notePublicId)

            assertEquals(mapOf(), repositories.noteFavourites.countsByNotes(setOf(notePublicId)))
        }
    }

    @Test
    fun `フォロワーでない相手でもお気に入りを押したときの鍵を引ける`() {
        withRepositories { repositories ->
            val favourites = repositories.noteFavourites
            favourites.add(favourite(actorUri = actorUri))

            assertEquals("pem of $actorUri", favourites.findPublicKeyPem(actorUri))
            assertNull(favourites.findPublicKeyPem(otherActorUri))
        }
    }

    @Test
    fun `お気に入りが残っていない相手の鍵は引けない`() {
        withRepositories { repositories ->
            val favourites = repositories.noteFavourites
            favourites.add(favourite(actorUri = actorUri))

            favourites.removeByActor(actorUri)

            assertNull(favourites.findPublicKeyPem(actorUri))
        }
    }

    @Test
    fun `相手のアクターを消すとお気に入りも消える`() {
        withRepositories { repositories ->
            repositories.noteFavourites.add(favourite(actorUri = actorUri))

            repositories.followers.removeRemoteActor(actorUri)

            assertEquals(mapOf(), repositories.noteFavourites.countsByNotes(setOf(notePublicId)))
        }
    }
}
