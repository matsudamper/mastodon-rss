package net.matsudamper.mastodon.rss.repository

import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.matsudamper.mastodon.rss.shared.PublicNoteId

// 本物の SQLite に対して確かめる。
// 相手はパーマリンクを後から引きに来るので、送ったものが残っていることが要件になる。
class NoteRepositoryTest {
    private val tempDir: Path = createTempDirectory("mastodon-rss-note-test")

    private val dbPath: Path = tempDir.resolve("test.db")

    private val now: Instant = Instant.parse("2026-08-10T00:00:00Z")

    init {
        TestSchema.applyTo(dbPath)
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun <T> withRepository(block: (NoteRepository) -> T): T =
        createRepositories(DatabaseConfig(path = dbPath)).use { block(it.notes) }

    private fun newNote(
        publicId: String,
        username: String = "admin",
        publishedAt: Instant = now,
    ): NewNote = NewNote(
        username = username,
        publicId = PublicNoteId(publicId),
        contentHtml = "<p>$publicId</p>",
        publishedAt = publishedAt,
    )

    @Test
    fun `記録した投稿を公開 id で引ける`() {
        withRepository { notes ->
            notes.add(newNote("abc"))

            val found = notes.find(PublicNoteId("abc"))
            assertEquals("abc", found?.publicId?.value)
            assertEquals("admin", found?.username)
            assertEquals("<p>abc</p>", found?.contentHtml)
            assertEquals(now, found?.publishedAt)

            assertNull(notes.find(PublicNoteId("none")))
        }
    }

    @Test
    fun `新しい順に返り、cursor で続きから取れる`() {
        withRepository { notes ->
            repeat(5) { index ->
                notes.add(newNote("note-$index", publishedAt = now.plusSeconds(index.toLong())))
            }

            val first = notes.list("admin", after = null, limit = 2)
            assertEquals(listOf("note-4", "note-3"), first.map { it.publicId.value })

            val second = notes.list("admin", after = first.last().cursor(), limit = 2)
            assertEquals(listOf("note-2", "note-1"), second.map { it.publicId.value })

            val third = notes.list("admin", after = second.last().cursor(), limit = 2)
            assertEquals(listOf("note-0"), third.map { it.publicId.value })
            assertEquals(emptyList(), notes.list("admin", after = third.last().cursor(), limit = 2))
        }
    }

    @Test
    fun `途中に投稿が増えても cursor なら取りこぼさない`() {
        withRepository { notes ->
            repeat(4) { index ->
                notes.add(newNote("note-$index", publishedAt = now.plusSeconds(index.toLong())))
            }

            val first = notes.list("admin", after = null, limit = 2)
            assertEquals(listOf("note-3", "note-2"), first.map { it.publicId.value })

            // 読んでいる間に新しい投稿が入る。件数で数えていたらここで 1 件ずれる
            notes.add(newNote("note-9", publishedAt = now.plusSeconds(99)))

            assertEquals(
                listOf("note-1", "note-0"),
                notes.list("admin", after = first.last().cursor(), limit = 2).map { it.publicId.value },
            )
        }
    }

    private fun Note.cursor(): NotePosition = NotePosition(publishedAt = publishedAt, publicId = publicId)

    @Test
    fun `同じ時刻でも並びが決まる`() {
        withRepository { notes ->
            repeat(3) { index -> notes.add(newNote("same-$index")) }

            // 公開日時が同じなら公開 id の降順。決めておかないと
            // ページをまたいで同じ投稿が 2 回出ることがある
            assertEquals(
                listOf("same-2", "same-1", "same-0"),
                notes.list("admin", after = null, limit = 10).map { it.publicId.value },
            )
        }
    }

    @Test
    fun `アカウントごとに分かれ、名前の大文字小文字は区別しない`() {
        withRepository { notes ->
            notes.add(newNote("a", username = "admin"))
            notes.add(newNote("b", username = "Feed1"))

            assertEquals(1, notes.count("admin"))
            assertEquals(listOf("b"), notes.list("feed1", after = null, limit = 10).map { it.publicId.value })
        }
    }

    @Test
    fun `アカウントの投稿をまとめて消せる`() {
        withRepository { notes ->
            notes.add(newNote("a", username = "Feed1"))
            notes.add(newNote("b", username = "feed1"))
            notes.add(newNote("c", username = "admin"))

            // 消した名前でアカウントを作り直したときに前の投稿が残らないよう、
            // 大文字小文字の違いは同じ名前として消す
            assertEquals(2, notes.deleteByUsername("FEED1"))
            assertEquals(0, notes.count("feed1"))
            assertEquals(1, notes.count("admin"))
        }
    }

    @Test
    fun `開き直しても投稿が残っている`() {
        withRepository { notes -> notes.add(newNote("abc")) }

        withRepository { notes ->
            assertEquals("<p>abc</p>", notes.find(PublicNoteId("abc"))?.contentHtml)
            assertEquals(1, notes.count("admin"))
        }
    }
}
