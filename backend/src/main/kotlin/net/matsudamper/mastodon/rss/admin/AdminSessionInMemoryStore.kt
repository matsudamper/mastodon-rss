package net.matsudamper.mastodon.rss.admin

import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

class AdminSessionInMemoryStore(
    private val ttl: Duration = DEFAULT_TTL,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val expirations = ConcurrentHashMap<String, Instant>()

    private val random = SecureRandom()

    val ttlSeconds: Long get() = ttl.inWholeSeconds

    fun create(): String {
        purgeExpired()

        val token = BASE64_ENCODER.encodeToString(ByteArray(TOKEN_SIZE_BYTES).also(random::nextBytes))
        expirations[token] = clock.instant().plus(ttl.toJavaDuration())
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
        val DEFAULT_TTL: Duration = 12.hours

        private const val TOKEN_SIZE_BYTES = 32

        private val BASE64_ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    }
}
