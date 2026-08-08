package net.matsudamper.mastodon.rss

import net.matsudamper.mastodon.rss.actor.ActorKeyConfig
import net.matsudamper.mastodon.rss.repository.DatabaseConfig
import net.matsudamper.mastodon.rss.staticfiles.StaticFilesConfig
import java.nio.file.Path

/**
 * 環境変数を読む唯一の場所。起動時にここで全部読み、以降は引数で配る。
 *
 * 各所で `System.getenv` を呼ぶと、どの変数が効くのかがコード全体を追わないと
 * 分からなくなる。テストからも差し替えられず、既定値の確認しかできない。
 * `:backend:repository` のようなライブラリ側のモジュールが環境を読むのも同じ理由でやめる。
 * 何をどこから読むかを決めるのはアプリの入口の仕事にする。
 *
 * native-image では起動時に環境変数を読む方が設定ファイルより素直なので、
 * 設定の入口そのものは環境変数に寄せている。
 */
data class AppConfig(
    val server: ServerConfig,
    val actorKey: ActorKeyConfig,
    val database: DatabaseConfig,
    val staticFiles: StaticFilesConfig,
) {
    companion object {
        const val ENV_HOST: String = "HOST"
        const val ENV_PORT: String = "PORT"
        const val ENV_DOMAIN: String = "DOMAIN"
        const val ENV_ACTOR_USERNAME: String = "ACTOR_USERNAME"
        const val ENV_DB_PATH: String = "DB_PATH"
        const val ENV_ACTOR_PRIVATE_KEY_PEM: String = "ACTOR_PRIVATE_KEY_PEM"
        const val ENV_ACTOR_PRIVATE_KEY_PATH: String = "ACTOR_PRIVATE_KEY_PATH"
        const val ENV_STATIC_SRC_DIR: String = "STATIC_SRC_DIR"

        const val DEFAULT_HOST: String = "0.0.0.0"
        const val DEFAULT_PORT: Int = 8080
        const val DEFAULT_ACTOR_USERNAME: String = "admin"
        const val DEFAULT_DB_PATH: String = "./data/mastodon-rss.db"
        const val DEFAULT_ACTOR_PRIVATE_KEY_PATH: String = "./data/actor-private-key.pem"

        fun fromEnvironment(): AppConfig = from(System::getenv)

        /**
         * 環境変数の読み取り元を差し替えられる形。テストから使う。
         *
         * [ENV_DOMAIN] だけは既定値を用意せず、無ければ落とす。適当な値で起動できると
         * `localhost` のようなドメインが焼き込まれたアクター ID を配ることになり、
         * Mastodon はリモートアクターを永続キャッシュするので相手側からは直せない。
         */
        internal fun from(getenv: (String) -> String?): AppConfig {
            // 空文字と空白だけの指定は未設定と同じに扱う。docker compose の
            // ${VAR} が空で展開されたときに、既定値へ落ちる方が扱いやすい
            fun read(name: String): String? = getenv(name)?.trim()?.takeIf { it.isNotEmpty() }

            val domain =
                read(ENV_DOMAIN)?.let(::normalizeDomain)
                    ?: throw IllegalArgumentException(
                        "$ENV_DOMAIN が未設定。WebFinger の acct とアクターの id に使うので必ず指定すること",
                    )

            return AppConfig(
                server =
                    ServerConfig(
                        host = read(ENV_HOST) ?: DEFAULT_HOST,
                        port = read(ENV_PORT)?.toIntOrNull() ?: DEFAULT_PORT,
                        domain = domain,
                        actorUsername = read(ENV_ACTOR_USERNAME) ?: DEFAULT_ACTOR_USERNAME,
                    ),
                actorKey = actorKeyConfig(getenv = getenv, path = read(ENV_ACTOR_PRIVATE_KEY_PATH)),
                database = DatabaseConfig(path = Path.of(read(ENV_DB_PATH) ?: DEFAULT_DB_PATH)),
                staticFiles = StaticFilesConfig(srcDir = read(ENV_STATIC_SRC_DIR)?.let(Path::of)),
            )
        }

        /**
         * 鍵の取得元を決める。
         *
         * 両方が設定されていたら落とす。片方を黙って無視すると、意図していない鍵で
         * 起動したことに気付けない。鍵が変わると相手側は署名検証に失敗し続けるうえ、
         * Mastodon はアクターをキャッシュするので後から直しても戻りが遅い。
         */
        private fun actorKeyConfig(
            getenv: (String) -> String?,
            path: String?,
        ): ActorKeyConfig {
            // PEM は中身をそのまま鍵として読むので、前後の空白も落とさずに渡す
            val pem = getenv(ENV_ACTOR_PRIVATE_KEY_PEM)?.takeIf { it.isNotBlank() }

            require(pem == null || path == null) {
                "$ENV_ACTOR_PRIVATE_KEY_PEM と $ENV_ACTOR_PRIVATE_KEY_PATH は同時に指定できない。どちらか一方にすること"
            }

            return if (pem != null) {
                ActorKeyConfig.Pem(pem)
            } else {
                ActorKeyConfig.File(Path.of(path ?: DEFAULT_ACTOR_PRIVATE_KEY_PATH))
            }
        }

        // アクター ID に焼き込まれる値なので、末尾の / は落としておく。
        // https://example.com/ のような URL ごと渡されることも考えて scheme も落とす
        private fun normalizeDomain(raw: String): String? =
            raw
                .removePrefix("https://")
                .removePrefix("http://")
                .trimEnd('/')
                .takeIf { it.isNotEmpty() }
    }
}
