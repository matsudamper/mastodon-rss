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
import net.matsudamper.mastodon.rss.repository.NoteBoostRepository.NewNoteBoost
import net.matsudamper.mastodon.rss.shared.PublicNoteId

class NoteBoostRepositoryTest {
    private val tempDir: Path = createTempDirectory("mastodon-rss-note-boost-test")

    private val dbPath: Path = tempDir.resolve("test.db")

    private val now: Instant = Instant.parse("2026-11-13T00:00:00Z")

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

    private fun boost(
        actorUri: String,
        notePublicId: PublicNoteId = this.notePublicId,
    ): NewNoteBoost = NewNoteBoost(
        notePublicId = notePublicId,
        actor = actor(actorUri),
        activityUri = "$actorUri/statuses/1/activity",
        receivedAt = now,
    )

    @Test
    fun `投稿ごとにブーストを数えられる`() {
        withRepositories { repositories ->
            val boosts = repositories.noteBoosts
            boosts.add(boost(actorUri = actorUri))
            boosts.add(boost(actorUri = otherActorUri))

            val counted = boosts.countsByNotes(setOf(notePublicId, PublicNoteId("none")))

            assertEquals(mapOf(notePublicId to 2), counted)
        }
    }

    @Test
    fun `同じ相手のブーストは増えない`() {
        withRepositories { repositories ->
            val boosts = repositories.noteBoosts
            assertTrue(boosts.add(boost(actorUri = actorUri)))

            assertFalse(boosts.add(boost(actorUri = actorUri)))

            assertEquals(mapOf(notePublicId to 1), boosts.countsByNotes(setOf(notePublicId)))
        }
    }

    @Test
    fun `配信していない投稿へのブーストは記録しない`() {
        withRepositories { repositories ->
            assertFalse(repositories.noteBoosts.add(boost(actorUri = actorUri, notePublicId = PublicNoteId("none"))))
        }
    }

    @Test
    fun `取り消しはAnnounceのidとブーストした相手で消せる`() {
        withRepositories { repositories ->
            val boosts = repositories.noteBoosts
            val activityUri = boost(actorUri = actorUri).activityUri
            boosts.add(boost(actorUri = actorUri))

            assertFalse(boosts.removeByActivity(actorUri = otherActorUri, activityUri = activityUri))
            assertTrue(boosts.removeByActivity(actorUri = actorUri, activityUri = activityUri))

            assertEquals(mapOf(), boosts.countsByNotes(setOf(notePublicId)))
        }
    }

    @Test
    fun `消えた相手のブーストをまとめて消せる`() {
        withRepositories { repositories ->
            val boosts = repositories.noteBoosts
            boosts.add(boost(actorUri = actorUri))
            boosts.add(boost(actorUri = otherActorUri))

            assertEquals(1, boosts.removeByActor(actorUri))

            assertEquals(mapOf(notePublicId to 1), boosts.countsByNotes(setOf(notePublicId)))
        }
    }

    @Test
    fun `投稿を消すとブーストも消える`() {
        withRepositories { repositories ->
            repositories.noteBoosts.add(boost(actorUri = actorUri))

            repositories.notes.delete(notePublicId)

            assertEquals(mapOf(), repositories.noteBoosts.countsByNotes(setOf(notePublicId)))
        }
    }

    @Test
    fun `ブーストが残っている相手の鍵だけ引ける`() {
        withRepositories { repositories ->
            val boosts = repositories.noteBoosts
            boosts.add(boost(actorUri = actorUri))

            assertEquals("pem of $actorUri", boosts.findPublicKeyPem(actorUri))
            assertNull(boosts.findPublicKeyPem(otherActorUri))

            boosts.removeByActor(actorUri)

            assertNull(boosts.findPublicKeyPem(actorUri))
        }
    }
}
