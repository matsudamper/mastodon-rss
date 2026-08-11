package net.matsudamper.mastodon.rss.admin

import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * 管理画面のログイン済みセッション。トークンをメモリ上に持つだけ。
 *
 * 署名付き Cookie にして状態を持たない形も考えたが、こちらにした。
 * 署名鍵をどこから渡すかという設定が 1 つ増えるうえ、鍵を固定すると
 * 「ログアウトさせる」手段が無くなる（発行済みの Cookie は期限まで有効なまま）。
 * 管理画面を開くのは運用者だけで、サーバーは 1 台、再起動も稀なので、
 * 再起動でログインし直しになる代わりに設定が増えない方を取る。
 *
 * 台数を増やす時が来たら、ここを DB に載せ替える。ルーティング側は
 * [create] と [isValid] しか見ていないので、差し替えはこのクラスの中で済む。
 *
 * トークンは [SecureRandom] の 32 バイト。総当たりで当てられる長さではないので、
 * 照合は [ConcurrentHashMap] の引きで済ませている（パスワードと違い、
 * 時間差から中身を推測されても意味のある情報にならない）。
 *
 * @param ttl 発行してから使えなくなるまでの時間
 * @param clock 期限の判定に使う時計。テストから進めるためだけに引数にしている
 */
class AdminSessions(
    private val ttl: Duration = DEFAULT_TTL,
    private val clock: Clock = Clock.systemUTC(),
) {
    /** トークンと、その期限 */
    private val expirations = ConcurrentHashMap<String, Instant>()

    private val random = SecureRandom()

    /** 期限までの秒数。Cookie の Max-Age に入れる */
    val ttlSeconds: Long get() = ttl.toSeconds()

    /**
     * 新しいトークンを発行する。
     *
     * 期限切れの後始末はここでやる。使われなくなったトークンは
     * [isValid] に来ないので、引かれるのを待っていると溜まり続ける。
     */
    fun create(): String {
        purgeExpired()

        val token = BASE64_ENCODER.encodeToString(ByteArray(TOKEN_SIZE_BYTES).also(random::nextBytes))
        expirations[token] = clock.instant().plus(ttl)
        return token
    }

    /**
     * 使えるトークンかどうか。
     *
     * 期限が切れていたらその場で捨てる。残しておいても false を返し続けるだけで、
     * 時計が戻れば再び通ってしまう。
     */
    fun isValid(token: String?): Boolean {
        val expiration = expirations[token ?: return false] ?: return false

        if (!expiration.isAfter(clock.instant())) {
            expirations.remove(token)
            return false
        }
        return true
    }

    /** ログアウト。知らないトークンを渡されても何もしない */
    fun remove(token: String?) {
        expirations.remove(token ?: return)
    }

    private fun purgeExpired() {
        val now = clock.instant()
        expirations.values.removeIf { !it.isAfter(now) }
    }

    companion object {
        /** Cookie の名前。frontend も同じものを消す */
        const val COOKIE_NAME: String = "admin_session"

        /**
         * 期限。開けっ放しのタブがそのまま使えると困るので短めにするが、
         * 作業の途中で切れると面倒なので 1 日は持たせる。
         */
        val DEFAULT_TTL: Duration = Duration.ofHours(12)

        /** トークンの長さ。当てられる長さでなければよいので 32 バイトにしている */
        private const val TOKEN_SIZE_BYTES = 32

        // Cookie の値になるので、区切りに使われる文字が出ない URL-safe を使う。
        // パディングの = も落としておく
        private val BASE64_ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    }
}
