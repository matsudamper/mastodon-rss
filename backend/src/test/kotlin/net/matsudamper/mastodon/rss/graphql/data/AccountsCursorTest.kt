package net.matsudamper.mastodon.rss.graphql.data

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.matsudamper.mastodon.rss.repository.AccountPosition
import net.matsudamper.mastodon.rss.shared.AccountId

class AccountsCursorTest {
    @Test
    fun `組み立てたものを解くと元に戻る`() {
        val cursor = AccountsCursor.of(POSITION)

        assertEquals(cursor, AccountsCursor.decode(cursor.encode()))
    }

    @Test
    fun `位置を入れて取り出すと元に戻る`() {
        assertEquals(POSITION, AccountsCursor.of(POSITION).toPosition())
    }

    @Test
    fun `中身は表に出ない形にする`() {
        val encoded = AccountsCursor.of(POSITION).encode()

        // そのまま読めるとクライアントが中身に依存する
        assertEquals(false, encoded.contains("afterId"))
    }

    @Test
    fun `base64として読めなければnull`() {
        assertNull(AccountsCursor.decode("これはカーソルではない"))
    }

    @Test
    fun `base64として読めてもJSONでなければnull`() {
        assertNull(AccountsCursor.decode("Zm9v"))
    }

    private companion object {
        val POSITION = AccountPosition(
            createdAt = Instant.parse("2026-08-16T00:00:00.5Z"),
            id = AccountId(12),
        )
    }
}
