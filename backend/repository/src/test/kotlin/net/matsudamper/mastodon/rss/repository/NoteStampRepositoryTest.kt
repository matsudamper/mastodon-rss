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
import net.matsudamper.mastodon.rss.repository.NoteStampRepository.NewNoteStamp
import net.matsudamper.mastodon.rss.repository.NoteStampRepository.StampCount
import net.matsudamper.mastodon.rss.shared.PublicNoteId

class NoteStampRepositoryTest {
    private val tempDir: Path = createTempDirectory("mastodon-rss-note-stamp-test")

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

    private fun stamp(
        actorUri: String,
        emoji: String,
        emojiImageUrl: String? = null,
    ): NewNoteStamp = NewNoteStamp(
        notePublicId = notePublicId,
        actor = actor(actorUri),
        emoji = emoji,
        emojiImageUrl = emojiImageUrl,
        receivedAt = now,
    )

    @Test
    fun `投稿ごとに絵文字の数を多い順に数えられる`() {
        withRepositories { repositories ->
            val stamps = repositories.noteStamps
            stamps.put(stamp(actorUri = actorUri, emoji = "👍"))
            stamps.put(stamp(actorUri = otherActorUri, emoji = ":kawaii:", emojiImageUrl = "https://remote.example/kawaii.png"))
            stamps.put(stamp(actorUri = "https://remote.example/users/carol", emoji = ":kawaii:"))

            val counted = stamps.countsByNotes(setOf(notePublicId, PublicNoteId("none")))

            assertEquals(
                mapOf(
                    notePublicId to listOf(
                        StampCount(emoji = ":kawaii:", emojiImageUrl = "https://remote.example/kawaii.png", count = 2),
                        StampCount(emoji = "👍", emojiImageUrl = null, count = 1),
                    ),
                ),
                counted,
            )
        }
    }

    @Test
    fun `同じ相手が押し直すと前のスタンプを置き換える`() {
        withRepositories { repositories ->
            val stamps = repositories.noteStamps
            assertTrue(stamps.put(stamp(actorUri = actorUri, emoji = "👍")))

            assertTrue(stamps.put(stamp(actorUri = actorUri, emoji = "🎉")))

            assertEquals(
                mapOf(notePublicId to listOf(StampCount(emoji = "🎉", emojiImageUrl = null, count = 1))),
                stamps.countsByNotes(setOf(notePublicId)),
            )
        }
    }

    @Test
    fun `配信していない投稿へのスタンプは記録しない`() {
        withRepositories { repositories ->
            val added = repositories.noteStamps.put(
                NewNoteStamp(
                    notePublicId = PublicNoteId("none"),
                    actor = actor(actorUri),
                    emoji = "👍",
                    emojiImageUrl = null,
                    receivedAt = now,
                ),
            )

            assertFalse(added)
        }
    }

    @Test
    fun `取り消しは投稿と押した相手と絵文字で消せる`() {
        withRepositories { repositories ->
            val stamps = repositories.noteStamps
            stamps.put(stamp(actorUri = actorUri, emoji = "👍"))

            assertFalse(stamps.remove(notePublicId = notePublicId, actorUri = otherActorUri, emoji = "👍"))
            assertFalse(stamps.remove(notePublicId = notePublicId, actorUri = actorUri, emoji = "🎉"))
            assertTrue(stamps.remove(notePublicId = notePublicId, actorUri = actorUri, emoji = "👍"))

            assertEquals(mapOf(), stamps.countsByNotes(setOf(notePublicId)))
        }
    }

    @Test
    fun `消えた相手のスタンプをまとめて消せる`() {
        withRepositories { repositories ->
            val stamps = repositories.noteStamps
            stamps.put(stamp(actorUri = actorUri, emoji = "👍"))
            stamps.put(stamp(actorUri = otherActorUri, emoji = "👍"))

            assertEquals(1, stamps.removeByActor(actorUri))

            assertEquals(
                mapOf(notePublicId to listOf(StampCount(emoji = "👍", emojiImageUrl = null, count = 1))),
                stamps.countsByNotes(setOf(notePublicId)),
            )
        }
    }

    @Test
    fun `投稿を消すとスタンプも消える`() {
        withRepositories { repositories ->
            repositories.noteStamps.put(stamp(actorUri = actorUri, emoji = "👍"))

            repositories.notes.delete(notePublicId)

            assertEquals(mapOf(), repositories.noteStamps.countsByNotes(setOf(notePublicId)))
        }
    }

    @Test
    fun `フォロワーでない相手でもスタンプを押したときの鍵を引ける`() {
        withRepositories { repositories ->
            val stamps = repositories.noteStamps
            stamps.put(stamp(actorUri = actorUri, emoji = "👍"))

            assertEquals("pem of $actorUri", stamps.findPublicKeyPem(actorUri))
            assertNull(stamps.findPublicKeyPem(otherActorUri))
        }
    }

    @Test
    fun `スタンプが残っていない相手の鍵は引けない`() {
        withRepositories { repositories ->
            val stamps = repositories.noteStamps
            stamps.put(stamp(actorUri = actorUri, emoji = "👍"))

            stamps.removeByActor(actorUri)

            assertNull(stamps.findPublicKeyPem(actorUri))
        }
    }

    @Test
    fun `相手のアクターを消すとスタンプも消える`() {
        withRepositories { repositories ->
            repositories.noteStamps.put(stamp(actorUri = actorUri, emoji = "👍"))

            repositories.followers.removeRemoteActor(actorUri)

            assertEquals(mapOf(), repositories.noteStamps.countsByNotes(setOf(notePublicId)))
        }
    }
}
