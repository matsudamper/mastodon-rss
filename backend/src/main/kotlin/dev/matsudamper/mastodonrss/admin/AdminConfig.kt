package dev.matsudamper.mastodonrss.admin

import dev.matsudamper.mastodonrss.admin.api.AdminApiPaths
import dev.matsudamper.mastodonrss.admin.api.AdminConfigNames
import dev.matsudamper.mastodonrss.crypto.PasswordHash
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * 管理画面の設定。
 *
 * パスワードハッシュを持たない状態を許す。ハッシュは管理画面の中で作らせる方針で、
 * 「起動 → /admin でハッシュを作る → 環境変数に入れて起動し直す」という順に
 * するため、最初の起動ではまだ値が存在しない。
 *
 * ハッシュが無い間はログインできない（[loginConfigured] が false）。
 * このときだけハッシュ生成 API を認証なしで開ける。設定後は締まる。
 *
 * @param passwordHash ログインに使うハッシュ。未設定なら null
 * @param sessionTtl ログイン状態を保つ長さ
 * @param cookieSecure セッション Cookie に `Secure` を付けるか
 */
data class AdminConfig(
    val passwordHash: PasswordHash?,
    val sessionTtl: Duration,
    val cookieSecure: Boolean,
) {
    /** ログインできる状態か。false ならハッシュが未設定 */
    val loginConfigured: Boolean get() = passwordHash != null

    init {
        require(sessionTtl > Duration.ZERO) {
            "$ENV_SESSION_TTL_MINUTES は 1 以上にすること: $sessionTtl"
        }
    }

    companion object {
        // 画面にも出す名前なので :shared に置いてある
        const val ENV_PASSWORD_HASH: String = AdminConfigNames.ENV_PASSWORD_HASH
        const val ENV_SESSION_TTL_MINUTES: String = "ADMIN_SESSION_TTL_MINUTES"
        const val ENV_COOKIE_SECURE: String = "ADMIN_COOKIE_SECURE"

        /** 既定のログイン保持時間。作業中に切れず、放置したまま残り続けもしない程度 */
        val DEFAULT_SESSION_TTL: Duration = 12.hours

        /**
         * 既定で Cookie に `Secure` を付ける。ActivityPub は HTTPS 前提で、
         * 本番は必ずリバースプロキシの内側に置くため。
         * http の localhost で試すときだけ [ENV_COOKIE_SECURE] を false にする。
         */
        const val DEFAULT_COOKIE_SECURE: Boolean = true

        fun fromEnvironment(): AdminConfig = from(System::getenv)

        /**
         * 環境変数の読み取り元を差し替えられる形。テストから使う。
         *
         * ハッシュが壊れていたら起動を止める。読めない値を持ったまま起動すると
         * ログインが必ず失敗するだけになり、パスワードを間違えたのか設定を
         * 間違えたのかが運用側から区別できない。
         */
        internal fun from(getenv: (String) -> String?): AdminConfig {
            val rawHash = getenv(ENV_PASSWORD_HASH)?.trim()?.takeIf { it.isNotEmpty() }
            val passwordHash =
                rawHash?.let {
                    try {
                        PasswordHash.parse(it)
                    } catch (e: IllegalArgumentException) {
                        throw IllegalArgumentException(
                            "$ENV_PASSWORD_HASH が読めない: ${e.message}。" +
                                "$ENV_PASSWORD_HASH を消して起動すると ${AdminApiPaths.PASSWORD_HASH_PAGE} で作り直せる",
                            e,
                        )
                    }
                }

            val sessionTtl =
                getenv(ENV_SESSION_TTL_MINUTES)?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
                    val minutes =
                        raw.toIntOrNull()
                            ?: throw IllegalArgumentException("$ENV_SESSION_TTL_MINUTES が数値ではない: $raw")
                    minutes.minutes
                } ?: DEFAULT_SESSION_TTL

            val cookieSecure =
                getenv(ENV_COOKIE_SECURE)?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
                    when (raw.lowercase()) {
                        "true" -> true

                        "false" -> false

                        else -> throw IllegalArgumentException(
                            "$ENV_COOKIE_SECURE は true か false にすること: $raw",
                        )
                    }
                } ?: DEFAULT_COOKIE_SECURE

            return AdminConfig(
                passwordHash = passwordHash,
                sessionTtl = sessionTtl,
                cookieSecure = cookieSecure,
            )
        }
    }
}
