package net.matsudamper.mastodon.rss.delivery

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

// 失敗が続いた inbox へしばらく送らない。数えるのは inbox ごとで、成功で数え直す。
class DeliveryCircuitBreakerTest {
    private val now: Instant = Instant.parse("2026-08-10T00:00:00Z")
    private val breaker = DeliveryCircuitBreaker(failureThreshold = 3, coolOff = 60.seconds)

    @Test
    fun `続けて失敗した回数が閾値に届くまでは送る`() {
        repeat(2) { breaker.recordFailure(INBOX, now) }

        assertEquals(false, breaker.isOpen(INBOX, now))
    }

    @Test
    fun `閾値に届いたら待つ間だけ止める`() {
        repeat(3) { breaker.recordFailure(INBOX, now) }

        assertEquals(true, breaker.isOpen(INBOX, now.plusSeconds(59)))
        assertEquals(false, breaker.isOpen(INBOX, now.plusSeconds(60)))
    }

    @Test
    fun `待ちが明けた後に 1 回失敗したらまた止める`() {
        repeat(3) { breaker.recordFailure(INBOX, now) }
        val afterCoolOff = now.plusSeconds(60)

        breaker.recordFailure(INBOX, afterCoolOff)

        assertEquals(true, breaker.isOpen(INBOX, afterCoolOff))
    }

    @Test
    fun `成功したら数え直す`() {
        repeat(2) { breaker.recordFailure(INBOX, now) }
        breaker.recordSuccess(INBOX)
        repeat(2) { breaker.recordFailure(INBOX, now) }

        assertEquals(false, breaker.isOpen(INBOX, now))
    }

    @Test
    fun `別の inbox の失敗は数えない`() {
        repeat(3) { breaker.recordFailure(INBOX, now) }

        assertEquals(false, breaker.isOpen("https://b.example/inbox", now))
    }

    private companion object {
        const val INBOX = "https://a.example/inbox"
    }
}
