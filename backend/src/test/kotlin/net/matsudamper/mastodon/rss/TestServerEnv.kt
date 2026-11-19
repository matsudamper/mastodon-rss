package net.matsudamper.mastodon.rss

/**
 * テストで使う `ServerEnv`。
 *
 * 必須の環境変数が無いと作れないので、テストからは常にこれを通す。
 * 変えたい変数だけ [of] に渡す。
 */
object TestServerEnv {
    const val DOMAIN: String = "example.com"

    /**
     * テストでよく使うアカウント名。環境変数ではなく、DB に入れるときに使う
     */
    const val USERNAME: String = "admin"

    val REQUIRED: Map<String, String> =
        mapOf(
            "HOST" to "0.0.0.0",
            "PORT" to "8080",
            "DOMAIN" to DOMAIN,
            "DB_PATH" to "./data/mastodon-rss.db",
            "ICON_CACHE_DIR" to "./data/image-cache",
            "ACTOR_PRIVATE_KEY_PATH" to "./data/actor-private-key.pem",
            "ADMIN_COOKIE_SECURE" to "true",
        )

    val value: ServerEnv = of()

    fun of(vararg values: Pair<String, String>): ServerEnv = ServerEnv(REQUIRED + values.toMap())
}
