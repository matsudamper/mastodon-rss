package net.matsudamper.mastodon.rss.admin

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// 期限は時計を進めて見るので、実時間で待つテストにはしない。
class AdminSessionsTest {
    private val clock = TestClock(Instant.parse("2026-08-11T00:00:00Z"))

    @Test
    fun `発行したトークンは有効`() {
        val sessions = AdminSessions(clock = clock)

        assertTrue(sessions.isValid(sessions.create()))
    }

    @Test
    fun `発行していないトークンは無効`() {
        val sessions = AdminSessions(clock = clock)
        sessions.create()

        assertFalse(sessions.isValid("知らないトークン"))
        assertFalse(sessions.isValid(null))
    }

    @Test
    fun `発行のたびに違うトークンになる`() {
        val sessions = AdminSessions(clock = clock)

        assertNotEquals(sessions.create(), sessions.create())
    }

    @Test
    fun `期限を過ぎたトークンは無効`() {
        val sessions = AdminSessions(ttl = Duration.ofHours(1), clock = clock)
        val token = sessions.create()

        clock.now = clock.now.plus(Duration.ofMinutes(59))
        assertTrue(sessions.isValid(token))

        clock.now = clock.now.plus(Duration.ofMinutes(2))
        assertFalse(sessions.isValid(token))
    }

    @Test
    fun `期限切れのトークンは時計を戻しても復活しない`() {
        val sessions = AdminSessions(ttl = Duration.ofHours(1), clock = clock)
        val token = sessions.create()

        clock.now = clock.now.plus(Duration.ofHours(2))
        assertFalse(sessions.isValid(token))

        clock.now = clock.now.minus(Duration.ofHours(2))
        assertFalse(sessions.isValid(token))
    }

    @Test
    fun `remove したトークンは無効`() {
        val sessions = AdminSessions(clock = clock)
        val token = sessions.create()

        sessions.remove(token)

        assertFalse(sessions.isValid(token))
    }

    @Test
    fun `remove しても他のトークンは残る`() {
        val sessions = AdminSessions(clock = clock)
        val removed = sessions.create()
        val kept = sessions.create()

        sessions.remove(removed)

        assertTrue(sessions.isValid(kept))
    }

    /** 進められる時計 */
    private class TestClock(
        var now: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = now
    }
}
