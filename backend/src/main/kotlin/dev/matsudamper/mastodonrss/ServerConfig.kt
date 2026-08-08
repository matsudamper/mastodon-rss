package dev.matsudamper.mastodonrss

import dev.matsudamper.mastodonrss.actor.ActorUsername

/**
 * サーバーの設定。
 *
 * native-image では起動時に環境変数を読む方が設定ファイルより素直なので、
 * `:repository` の `DatabaseConfig` と同じく入口は環境変数に寄せる。
 *
 * @param host バインドするアドレス
 * @param port 待ち受けポート
 * @param domain 外部に公開するドメイン。WebFinger の `acct:` と Actor の `id` に使う
 * @param actorUsername 固定アクターのユーザー名。`acct:<name>@<domain>` と
 *   `/users/<name>` の両方に入る。Phase 6 で複数アクターにするまでは 1 つだけ
 */
data class ServerConfig(
    val host: String,
    val port: Int,
    val domain: String,
    val actorUsername: String,
) {
    init {
        require(ActorUsername.isValid(actorUsername)) {
            "$ENV_ACTOR_USERNAME が使えない形式: $actorUsername。" +
                "英数字と _ . - のみ、先頭と末尾は英数字か _ にすること"
        }
    }

    companion object {
        const val ENV_HOST: String = "HOST"
        const val ENV_PORT: String = "PORT"
        const val ENV_DOMAIN: String = "DOMAIN"
        const val ENV_ACTOR_USERNAME: String = "ACTOR_USERNAME"

        const val DEFAULT_HOST: String = "0.0.0.0"
        const val DEFAULT_PORT: Int = 8080
        const val DEFAULT_ACTOR_USERNAME: String = "admin"

        fun fromEnvironment(): ServerConfig = from(System::getenv)

        /**
         * 環境変数の読み取り元を差し替えられる形。テストから使う。
         *
         * [ENV_DOMAIN] は必須にしている。既定値を用意して起動できてしまうと、
         * `localhost` のようなドメインが焼き込まれたアクター ID を配ることになる。
         * Mastodon はリモートアクターを永続キャッシュするので、間違ったまま
         * 一度でも取得されると相手側からは直せない。落とす方が安い。
         */
        internal fun from(getenv: (String) -> String?): ServerConfig {
            val domain =
                getenv(ENV_DOMAIN)?.let(::normalizeDomain)
                    ?: throw IllegalArgumentException(
                        "$ENV_DOMAIN が未設定。WebFinger の acct とアクターの id に使うので必ず指定すること",
                    )

            return ServerConfig(
                host = getenv(ENV_HOST)?.takeIf { it.isNotBlank() } ?: DEFAULT_HOST,
                port = getenv(ENV_PORT)?.trim()?.toIntOrNull() ?: DEFAULT_PORT,
                domain = domain,
                actorUsername =
                    getenv(ENV_ACTOR_USERNAME)?.trim()?.takeIf { it.isNotEmpty() }
                        ?: DEFAULT_ACTOR_USERNAME,
            )
        }

        // アクター ID に焼き込まれる値なので、前後の空白や末尾の / は落としておく。
        // https://example.com/ のような URL ごと渡されることも考えて scheme も落とす
        private fun normalizeDomain(raw: String): String? =
            raw
                .trim()
                .removePrefix("https://")
                .removePrefix("http://")
                .trimEnd('/')
                .takeIf { it.isNotEmpty() }
    }
}
