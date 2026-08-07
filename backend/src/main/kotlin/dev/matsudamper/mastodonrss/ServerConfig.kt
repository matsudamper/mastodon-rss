package dev.matsudamper.mastodonrss

/**
 * サーバーの設定。
 *
 * native-image では起動時に環境変数を読む方が設定ファイルより素直なので、
 * `:repository` の `DatabaseConfig` と同じく入口は環境変数に寄せる。
 *
 * @param host バインドするアドレス
 * @param port 待ち受けポート
 * @param domain 外部に公開するドメイン。WebFinger の `acct:` と Actor の `id` に使う。
 *   Phase 1 に入るまでは使い道が無いので未設定を許している
 */
data class ServerConfig(
    val host: String,
    val port: Int,
    val domain: String?,
) {
    companion object {
        const val ENV_HOST: String = "HOST"
        const val ENV_PORT: String = "PORT"
        const val ENV_DOMAIN: String = "DOMAIN"

        const val DEFAULT_HOST: String = "0.0.0.0"
        const val DEFAULT_PORT: Int = 8080

        fun fromEnvironment(): ServerConfig = from(System::getenv)

        /**
         * 環境変数の読み取り元を差し替えられる形。テストから使う。
         */
        internal fun from(getenv: (String) -> String?): ServerConfig {
            return ServerConfig(
                host = getenv(ENV_HOST)?.takeIf { it.isNotBlank() } ?: DEFAULT_HOST,
                port = getenv(ENV_PORT)?.trim()?.toIntOrNull() ?: DEFAULT_PORT,
                // アクター ID に焼き込まれる値なので、前後の空白や末尾の / は落としておく。
                // https://example.com/ のような URL ごと渡されることも考えて scheme も落とす
                domain = getenv(ENV_DOMAIN)?.let(::normalizeDomain),
            )
        }

        private fun normalizeDomain(raw: String): String? {
            return raw.trim()
                .removePrefix("https://")
                .removePrefix("http://")
                .trimEnd('/')
                .takeIf { it.isNotEmpty() }
        }
    }
}
