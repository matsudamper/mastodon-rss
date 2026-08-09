package net.matsudamper.mastodon.rss

/**
 * テストで使う `ServerConfig`。
 *
 * `module` は設定を引数で受け取るので、テストからはこれを渡す。
 */
object TestServerConfig {
    const val DOMAIN: String = "example.com"
    const val USERNAME: String = "admin"

    val value: ServerConfig =
        ServerConfig(
            host = AppConfig.DEFAULT_HOST,
            port = AppConfig.DEFAULT_PORT,
            domain = DOMAIN,
            actorUsername = USERNAME,
        )
}
