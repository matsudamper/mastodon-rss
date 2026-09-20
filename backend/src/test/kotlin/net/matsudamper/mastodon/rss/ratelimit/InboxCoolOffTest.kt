package net.matsudamper.mastodon.rss.ratelimit

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// 署名を拒否した送信元を通さないでおく記録。
// 止まらないと、通らない署名を送り続けるだけで取得もメモリも増やせる。
class InboxCoolOffTest {
    private val start: Instant = Instant.parse("2026-09-20T00:00:00Z")

    private val clock = MutableClock(start)

    private fun coolOff(
        maxClients: Int = 10,
        maxHistory: Int = 10,
    ): InboxCoolOff =
        InboxCoolOff(
            coolOff = Duration.ofMinutes(5),
            maxClients = maxClients,
            maxHistory = maxHistory,
            clock = clock,
        )

    @Test
    fun `拒否するまでは通す`() {
        assertNull(coolOff().blockedUntil("203.0.113.1"))
    }

    @Test
    fun `拒否したら通さない`() {
        val coolOff = coolOff()

        coolOff.rejected("203.0.113.1")

        assertEquals(start.plus(Duration.ofMinutes(5)), coolOff.blockedUntil("203.0.113.1"))
    }

    @Test
    fun `別の送信元は通す`() {
        val coolOff = coolOff()
        coolOff.rejected("203.0.113.1")

        assertNull(coolOff.blockedUntil("203.0.113.2"))
    }

    @Test
    fun `時間が経てば通す`() {
        val coolOff = coolOff()
        coolOff.rejected("203.0.113.1")

        clock.now = start.plus(Duration.ofMinutes(5))

        assertNull(coolOff.blockedUntil("203.0.113.1"))
        assertEquals(listOf(), coolOff.active())
    }

    @Test
    fun `拒否し直すと通さない時間が延びる`() {
        val coolOff = coolOff()
        coolOff.rejected("203.0.113.1")

        clock.now = start.plus(Duration.ofMinutes(4))
        coolOff.rejected("203.0.113.1")

        assertEquals(start.plus(Duration.ofMinutes(9)), coolOff.blockedUntil("203.0.113.1"))
    }

    @Test
    fun `止めている間に来た数を数える`() {
        val coolOff = coolOff()
        coolOff.rejected("203.0.113.1")

        coolOff.blockedUntil("203.0.113.1")
        coolOff.blockedUntil("203.0.113.1")

        val active = assertNotNull(coolOff.active().singleOrNull())
        assertEquals("203.0.113.1", active.clientIp)
        assertEquals(1, active.rejectedCount)
        assertEquals(2, active.blockedRequestCount)
    }

    @Test
    fun `止めた記録は新しい順に残る`() {
        val coolOff = coolOff()
        coolOff.rejected("203.0.113.1")

        clock.now = start.plus(Duration.ofMinutes(1))
        coolOff.rejected("203.0.113.2")

        assertEquals(
            listOf("203.0.113.2", "203.0.113.1"),
            coolOff.history(limit = 10).map { it.clientIp },
        )
    }

    @Test
    fun `記録は上限を超えたら古いものから消える`() {
        val coolOff = coolOff(maxHistory = 2)

        coolOff.rejected("203.0.113.1")
        coolOff.rejected("203.0.113.2")
        coolOff.rejected("203.0.113.3")

        assertEquals(
            listOf("203.0.113.3", "203.0.113.2"),
            coolOff.history(limit = 10).map { it.clientIp },
        )
    }

    @Test
    fun `覚える送信元は上限を超えない`() {
        // 送信元はいくらでも増やせる。送り込まれてもメモリが増え続けないこと
        val coolOff = coolOff(maxClients = 2)

        repeat(10) { index -> coolOff.rejected("203.0.113.$index") }

        assertEquals(2, coolOff.active().size)
    }

    private class MutableClock(
        var now: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = now
    }
}
