package dev.matsudamper.mastodonrss

/**
 * テストで使う `ServerConfig`。
 *
 * `ServerConfig.fromEnvironment()` は `DOMAIN` が無いと落ちるので、
 * `module` を呼ぶテストは環境変数に頼らずこれを渡す。
 */
object TestServerConfig {
    const val DOMAIN: String = "example.com"
    const val USERNAME: String = "admin"

    val value: ServerConfig =
        ServerConfig(
            host = ServerConfig.DEFAULT_HOST,
            port = ServerConfig.DEFAULT_PORT,
            domain = DOMAIN,
            actorUsername = USERNAME,
        )
}
