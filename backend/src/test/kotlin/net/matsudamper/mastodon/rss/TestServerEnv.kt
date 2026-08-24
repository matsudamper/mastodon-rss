package net.matsudamper.mastodon.rss

/**
 * テストで使う `ServerEnv`。
 *
 * `DOMAIN` が無いと作れないので、テストからは常にこれを通す。
 * 変えたい変数だけ [of] に渡す。
 */
object TestServerEnv {
    const val DOMAIN: String = "example.com"

    /**
     * テストでよく使うアカウント名。環境変数ではなく、DB に入れるときに使う
     */
    const val USERNAME: String = "admin"

    val value: ServerEnv = of()

    fun of(vararg values: Pair<String, String>): ServerEnv = ServerEnv(values.toMap() + ("DOMAIN" to DOMAIN))
}
