package net.matsudamper.mastodon.rss.admin

import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/** ログイン済みセッション。メモリ上に持つので、再起動すると消える */
class AdminSessions(
    private val ttl: Duration = DEFAULT_TTL,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val expirations = ConcurrentHashMap<String, Instant>()

    private val random = SecureRandom()

    val ttlSeconds: Long get() = ttl.toSeconds()

    fun create(): String {
        purgeExpired()

        val token = BASE64_ENCODER.encodeToString(ByteArray(TOKEN_SIZE_BYTES).also(random::nextBytes))
        expirations[token] = clock.instant().plus(ttl)
        return token
    }

    fun isValid(token: String?): Boolean {
        val expiration = expirations[token ?: return false] ?: return false

        if (!expiration.isAfter(clock.instant())) {
            expirations.remove(token)
            return false
        }
        return true
    }

    fun remove(token: String?) {
        expirations.remove(token ?: return)
    }

    private fun purgeExpired() {
        val now = clock.instant()
        expirations.values.removeIf { !it.isAfter(now) }
    }

    companion object {
        const val COOKIE_NAME: String = "admin_session"

        val DEFAULT_TTL: Duration = Duration.ofHours(12)

        private const val TOKEN_SIZE_BYTES = 32

        private val BASE64_ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    }
}
