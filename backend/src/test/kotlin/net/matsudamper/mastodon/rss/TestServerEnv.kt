package net.matsudamper.mastodon.rss

import java.nio.file.Path

/**
 * テストで使う `ServerEnv`。
 *
 * `module` は設定を引数で受け取るので、テストからはこれを渡す。
 * 一部だけ変えたいときは `copy` する。
 */
object TestServerEnv {
    const val DOMAIN: String = "example.com"
    const val USERNAME: String = "admin"

    val value: ServerEnv =
        ServerEnv(
            host = "0.0.0.0",
            port = 8080,
            domain = DOMAIN,
            actorUsername = USERNAME,
            dbPath = Path.of("./data/mastodon-rss.db"),
            actorPrivateKey = ServerEnv.ActorPrivateKey.File(Path.of("./data/actor-private-key.pem")),
            staticSrcDir = null,
        )
}
