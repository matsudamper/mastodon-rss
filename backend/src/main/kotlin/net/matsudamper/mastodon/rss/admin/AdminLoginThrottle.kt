package net.matsudamper.mastodon.rss.admin

import java.net.Inet6Address
import java.net.InetAddress
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
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

    private val nextPurgeAt = AtomicReference(Instant.MIN)

    /**
     * false なら締め出し中なので照合しない。締め出しは最後の試行から lockDuration に延びる
     */
    fun tryReserve(clientIp: String): Boolean {
        val now = clock.instant()
        purgeExpiredIfDue(now)

        val expiresAt = now.plus(lockDuration.toJavaDuration())
        var reserved = false
        failures.compute(sourceKeyOf(clientIp)) { _, current ->
            val count = current?.takeIf { it.expiresAt.isAfter(now) }?.count ?: 0
            reserved = count < maxFailures
            Failures(count = if (reserved) count + 1 else count, expiresAt = expiresAt)
        }
        return reserved
    }

    fun recordSuccess(clientIp: String) {
        failures.remove(sourceKeyOf(clientIp))
    }

    /**
     * 記録が多いと全件走査が重く、毎回走らせると締め出されたリクエストだけで CPU を使わせられる
     */
    private fun purgeExpiredIfDue(now: Instant) {
        val scheduled = nextPurgeAt.get()
        if (now.isBefore(scheduled)) return
        if (!nextPurgeAt.compareAndSet(scheduled, now.plus(PURGE_INTERVAL.toJavaDuration()))) return

        failures.values.removeIf { !it.expiresAt.isAfter(now) }
    }

    /**
     * IPv6 は 1 契約に /64 以上が割り当てられ、アドレスを替えながら試せる。
     * アドレスごとに数えると締め出しを逃れられるうえ、記録も際限なく増えるので /64 でまとめる
     */
    private fun sourceKeyOf(clientIp: String): String {
        if (!clientIp.contains(':')) return clientIp
        // ':' を含むならホスト名ではなく IPv6 のリテラルなので、名前解決は起きない
        val address = runCatching { InetAddress.getByName(clientIp) }.getOrNull()
        if (address !is Inet6Address) return clientIp
        return address.address
            .copyOfRange(0, IPV6_PREFIX_BYTES)
            .joinToString(separator = "", postfix = "::/64") { "%02x".format(it) }
    }

    private data class Failures(
        val count: Int,
        val expiresAt: Instant,
    )

    companion object {
        private const val DEFAULT_MAX_FAILURES = 5
        private val DEFAULT_LOCK_DURATION: Duration = 30.days
        private val PURGE_INTERVAL: Duration = 1.minutes
        private const val IPV6_PREFIX_BYTES = 8
    }
}
