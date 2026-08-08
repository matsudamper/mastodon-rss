package dev.matsudamper.mastodonrss.admin

import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

/**
 * 管理画面のログインセッション。
 *
 * トークンはメモリだけに持ち、DB にも Cookie の署名にも残さない。
 * 再起動すると全員ログアウトになるが、管理画面を使うのは運用者一人なので
 * 実害が無く、鍵や署名の管理を増やさずに済む。
 *
 * 署名付き Cookie（トークンを Cookie 側に持たせる方式）にしなかったのは、
 * 署名鍵をどこに置くかという問題が増えるため。起動ごとにランダムな鍵を作ると
 * 結局は再起動でログアウトするので、素直にサーバー側で覚える。
 *
 * @param ttl 発行したトークンが有効な長さ
 * @param now 現在時刻（エポックミリ秒）。テストから差し替える
 */
class AdminSessions(
    private val ttl: Duration,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** トークン → 期限（エポックミリ秒） */
    private val tokens = ConcurrentHashMap<String, Long>()

    private val secureRandom = SecureRandom()

    /**
     * 覚えているトークンの数。掃除されていない期限切れも含む。
     * 溜まり続けていないことをテストから確かめるために見えるようにしている。
     */
    internal val storedCount: Int get() = tokens.size

    /**
     * 新しいトークンを発行する。
     *
     * ログインのたびに期限切れを掃除する。管理画面のログイン頻度なら
     * これで十分で、掃除のためのスレッドを増やさずに済む。
     */
    fun issue(): String {
        purgeExpired()

        val bytes = ByteArray(TOKEN_SIZE_BYTES).also(secureRandom::nextBytes)
        val token = BASE64_ENCODER.encodeToString(bytes)
        tokens[token] = now() + ttl.inWholeMilliseconds
        return token
    }

    /** トークンが有効か。null や期限切れは false */
    fun isValid(token: String?): Boolean {
        if (token == null) return false

        val expiresAt = tokens[token] ?: return false
        if (expiresAt <= now()) {
            tokens.remove(token)
            return false
        }
        return true
    }

    /** ログアウト。知らないトークンを渡されても何も起きない */
    fun revoke(token: String?) {
        if (token != null) {
            tokens.remove(token)
        }
    }

    private fun purgeExpired() {
        val current = now()
        tokens.entries.removeIf { (_, expiresAt) -> expiresAt <= current }
    }

    private companion object {
        /**
         * トークンの長さ。推測されないことだけが要件なので、
         * 総当たりが現実的でない 256bit にしている。
         */
        const val TOKEN_SIZE_BYTES = 32

        // Cookie の値に使うので、= や + が出ない URL-safe にする
        val BASE64_ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    }
}
