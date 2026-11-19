package net.matsudamper.mastodon.rss.admin

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class AdminLoginThrottleTest {
    private val clock = TestClock(Instant.parse("2026-08-11T00:00:00Z"))

    @Test
    fun `5 回までは照合できる`() {
        val throttle = throttle()

        repeat(5) { assertTrue(throttle.tryReserve(clientIp = "192.0.2.1")) }
        assertFalse(throttle.tryReserve(clientIp = "192.0.2.1"))
    }

    @Test
    fun `締め出しは IP ごと`() {
        val throttle = throttle()
        repeat(5) { throttle.tryReserve(clientIp = "192.0.2.1") }

        assertTrue(throttle.tryReserve(clientIp = "192.0.2.2"))
    }

    @Test
    fun `IPv6 は同じ 64 ビットのプレフィックスならまとめて数える`() {
        val throttle = throttle()
        (1..5).forEach { throttle.tryReserve(clientIp = "2001:db8:0:1::$it") }

        assertFalse(throttle.tryReserve(clientIp = "2001:db8:0:1:ffff::1"))
        assertTrue(throttle.tryReserve(clientIp = "2001:db8:0:2::1"))
    }

    @Test
    fun `成功すると失敗の記録が消える`() {
        val throttle = throttle()
        repeat(4) { throttle.tryReserve(clientIp = "192.0.2.1") }
        throttle.recordSuccess(clientIp = "192.0.2.1")

        repeat(5) { assertTrue(throttle.tryReserve(clientIp = "192.0.2.1")) }
    }

    @Test
    fun `締め出し中に試すと最後の試行から期限が延びる`() {
        val throttle = throttle()
        repeat(5) { throttle.tryReserve(clientIp = "192.0.2.1") }

        clock.now = clock.now.plus(Duration.ofMinutes(59))
        assertFalse(throttle.tryReserve(clientIp = "192.0.2.1"))

        clock.now = clock.now.plus(Duration.ofMinutes(59))
        assertFalse(throttle.tryReserve(clientIp = "192.0.2.1"))

        clock.now = clock.now.plus(Duration.ofMinutes(61))
        assertTrue(throttle.tryReserve(clientIp = "192.0.2.1"))
    }

    private fun throttle() = AdminLoginThrottle(maxFailures = 5, lockDuration = 1.hours, clock = clock)

    /**
     * 進められる時計
     */
    private class TestClock(
        var now: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = now
    }
}
