package net.matsudamper.mastodon.rss.graphql.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountsCursorTest {
    @Test
    fun `組み立てたものを解くと元に戻る`() {
        val cursor = AccountsCursor(afterUsername = "feed1")

        assertEquals(cursor, AccountsCursor.decode(cursor.encode()))
    }

    @Test
    fun `中身は表に出ない形にする`() {
        val encoded = AccountsCursor(afterUsername = "feed1").encode()

        // そのまま読めるとクライアントが中身に依存する
        assertEquals(false, encoded.contains("feed1"))
    }

    @Test
    fun `base64として読めなければnull`() {
        assertNull(AccountsCursor.decode("これはカーソルではない"))
    }

    @Test
    fun `base64として読めてもJSONでなければnull`() {
        assertNull(AccountsCursor.decode("Zm9v"))
    }
}
