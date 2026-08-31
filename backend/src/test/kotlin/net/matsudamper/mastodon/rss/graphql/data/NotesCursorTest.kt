package net.matsudamper.mastodon.rss.graphql.data

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.matsudamper.mastodon.rss.note.NotePosition
import net.matsudamper.mastodon.rss.shared.PublicNoteId

class NotesCursorTest {
    private val position = NotePosition(
        publishedAt = Instant.parse("2026-08-19T00:00:00.123456789Z"),
        publicId = PublicNoteId("5b0d2b9a-0000-4000-8000-000000000000"),
    )

    @Test
    fun `組み立てたものを解くと元に戻る`() {
        val cursor = NotesCursor.of(position)

        assertEquals(cursor, NotesCursor.decode(cursor.encode()))
        // 秒未満まで戻らないと、同じ秒の投稿がページの境目で落ちる
        assertEquals(position, NotesCursor.decode(cursor.encode())?.toPosition())
    }

    @Test
    fun `中身は表に出ない形にする`() {
        val encoded = NotesCursor.of(position).encode()

        assertEquals(false, encoded.contains(position.publicId.value))
    }

    @Test
    fun `base64として読めなければnull`() {
        assertNull(NotesCursor.decode("これはカーソルではない"))
    }

    @Test
    fun `base64として読めてもJSONでなければnull`() {
        assertNull(NotesCursor.decode("Zm9v"))
    }
}
