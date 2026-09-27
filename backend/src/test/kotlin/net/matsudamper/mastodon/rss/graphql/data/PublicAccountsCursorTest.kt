package net.matsudamper.mastodon.rss.graphql.data

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.matsudamper.mastodon.rss.repository.AccountPosition
import net.matsudamper.mastodon.rss.repository.LatestNoteAccountPosition
import net.matsudamper.mastodon.rss.shared.AccountId

class PublicAccountsCursorTest {
    @Test
    fun `追加した順の位置を入れて取り出すと元に戻る`() {
        val decoded = PublicAccountsCursor.decode(PublicAccountsCursor.of(ADDED_POSITION).encode())

        assertEquals(ADDED_POSITION, decoded?.toAccountPosition())
        assertNull(decoded?.toLatestNoteAccountPosition())
    }

    @Test
    fun `最後に投稿した順の位置を入れて取り出すと元に戻る`() {
        val decoded = PublicAccountsCursor.decode(PublicAccountsCursor.of(LATEST_NOTE_POSITION).encode())

        assertEquals(LATEST_NOTE_POSITION, decoded?.toLatestNoteAccountPosition())
        assertNull(decoded?.toAccountPosition())
    }

    @Test
    fun `投稿の無い位置も元に戻る`() {
        val position = LATEST_NOTE_POSITION.copy(latestNoteAt = null)

        val decoded = PublicAccountsCursor.decode(PublicAccountsCursor.of(position).encode())

        assertEquals(position, decoded?.toLatestNoteAccountPosition())
    }

    @Test
    fun `base64として読めなければnull`() {
        assertNull(PublicAccountsCursor.decode("これはカーソルではない"))
    }

    @Test
    fun `時刻に直せない秒ならnull`() {
        val encoded = PublicAccountsCursor(
            order = PublicAccountsCursor.Order.ADDED_NEWEST,
            epochSecond = Long.MAX_VALUE,
            nano = 0,
            id = 1,
            notesUpToId = null,
        ).encode()

        assertNull(PublicAccountsCursor.decode(encoded))
    }

    private companion object {
        val ADDED_POSITION = AccountPosition(
            createdAt = Instant.parse("2026-08-16T00:00:00.5Z"),
            id = AccountId(12),
        )

        val LATEST_NOTE_POSITION = LatestNoteAccountPosition(
            notesUpToId = 34,
            latestNoteAt = Instant.parse("2026-08-17T00:00:00.25Z"),
            id = AccountId(12),
        )
    }
}
