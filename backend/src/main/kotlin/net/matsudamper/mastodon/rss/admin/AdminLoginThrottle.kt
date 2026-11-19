package net.matsudamper.mastodon.rss.admin

import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaDuration

/**
 * 照合の前に [tryReserve] で失敗を 1 回分先に数える。
 * 照合の後に数えると、同時に送られたリクエストが数える前の判定をすり抜けて上限を超えて照合できる
 */
class AdminLoginThrottle(
    private val maxFailures: Int = DEFAULT_MAX_FAILURES,
    private val lockDuration: Duration = DEFAULT_LOCK_DURATION,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val failures = ConcurrentHashMap<String, Failures>()

    /**
     * false なら締め出し中なので照合しない。締め出しは最後の試行から lockDuration に延びる
     */
    fun tryReserve(clientIp: String): Boolean {
        purgeExpired()

        val now = clock.instant()
        val expiresAt = now.plus(lockDuration.toJavaDuration())
        var reserved = false
        failures.compute(clientIp) { _, current ->
            val count = current?.takeIf { it.expiresAt.isAfter(now) }?.count ?: 0
            reserved = count < maxFailures
            Failures(count = if (reserved) count + 1 else count, expiresAt = expiresAt)
        }
        return reserved
    }

    fun recordSuccess(clientIp: String) {
        failures.remove(clientIp)
    }

    private fun purgeExpired() {
        val now = clock.instant()
        failures.values.removeIf { !it.expiresAt.isAfter(now) }
    }

    private data class Failures(
        val count: Int,
        val expiresAt: Instant,
    )

    companion object {
        private const val DEFAULT_MAX_FAILURES = 5
        private val DEFAULT_LOCK_DURATION: Duration = 30.days
    }
}
