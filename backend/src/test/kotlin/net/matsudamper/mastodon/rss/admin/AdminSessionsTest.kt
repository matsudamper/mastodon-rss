package net.matsudamper.mastodon.rss.admin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

// ログインセッション。時刻は差し替えて期限切れを確かめる。
class AdminSessionsTest {
    private var now: Long = 0
    private val sessions = AdminSessions(ttl = 10.minutes, now = { now })

    @Test
    fun `発行したトークンは有効になる`() {
        val token = sessions.issue()

        assertTrue(sessions.isValid(token))
    }

    @Test
    fun `知らないトークンは無効`() {
        sessions.issue()

        assertFalse(sessions.isValid("知らないトークン"))
        assertFalse(sessions.isValid(null))
    }

    @Test
    fun `毎回違うトークンになる`() {
        val tokens = List(10) { sessions.issue() }

        assertEquals(10, tokens.toSet().size)
    }

    @Test
    fun `期限を過ぎると無効になる`() {
        val token = sessions.issue()

        now += 10.minutes.inWholeMilliseconds
        assertFalse(sessions.isValid(token))
    }

    @Test
    fun `期限内なら有効なまま`() {
        val token = sessions.issue()

        now += 9.minutes.inWholeMilliseconds
        assertTrue(sessions.isValid(token))
    }

    @Test
    fun `ログアウトすると無効になる`() {
        val token = sessions.issue()

        sessions.revoke(token)

        assertFalse(sessions.isValid(token))
    }

    @Test
    fun `他のセッションはログアウトの影響を受けない`() {
        val first = sessions.issue()
        val second = sessions.issue()

        sessions.revoke(first)

        assertTrue(sessions.isValid(second))
    }

    @Test
    fun `期限切れは溜め込まない`() {
        repeat(5) { sessions.issue() }
        now += 20.minutes.inWholeMilliseconds

        sessions.issue()

        // 掃除していないと、ログインのたびにトークンが残り続ける
        assertEquals(1, sessions.storedCount)
    }
}
