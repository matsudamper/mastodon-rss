package net.matsudamper.mastodon.rss.admin

import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * ログイン済みセッション。トークンをメモリ上に持つだけで、再起動すると消える。
 *
 * 署名付き Cookie にしないのは、署名鍵の設定が増えるうえ、鍵を固定すると
 * ログアウトさせる手段が無くなるため。
 *
 * @param clock 期限の判定に使う。テストから進めるためだけに引数にしている
 */
class AdminSessions(
    private val ttl: Duration = DEFAULT_TTL,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val expirations = ConcurrentHashMap<String, Instant>()

    private val random = SecureRandom()

    /** 期限までの秒数。Cookie の Max-Age に入れる */
    val ttlSeconds: Long get() = ttl.toSeconds()

    /** 新しいトークンを発行する。使われないまま期限切れになったものはここで捨てる */
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

        // Cookie の値になるので、区切りに使われる文字が出ない URL-safe にする
        private val BASE64_ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    }
}
