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
         * 空文字と空白だけの指定はどれも未設定と同じに扱う。docker compose の
         * `${VAR}` が空で展開されたときに、既定値へ落ちる方が扱いやすい。
         */
        internal fun from(getenv: (String) -> String?): AppConfig {
            val host: String =
                run {
                    val raw = getenv(ENV_HOST)?.trim()
                    if (raw.isNullOrEmpty()) DEFAULT_HOST else raw
                }

            val port: Int =
                run {
                    // 数値でなければ既定値に落とす
                    getenv(ENV_PORT)?.trim()?.toIntOrNull() ?: DEFAULT_PORT
                }

            val domain: String =
                run {
                    // アクター ID に焼き込まれる値なので、末尾の / は落としておく。
                    // https://example.com/ のような URL ごと渡されることも考えて scheme も落とす
                    val normalized =
                        getenv(ENV_DOMAIN)
                            ?.trim()
                            ?.removePrefix("https://")
                            ?.removePrefix("http://")
                            ?.trimEnd('/')

                    // 既定値を用意して起動できてしまうと、localhost のようなドメインが
                    // 焼き込まれたアクター ID を配ることになる。Mastodon はリモートアクターを
                    // 永続キャッシュするので、相手側からは直せない。落とす方が安い
                    require(!normalized.isNullOrEmpty()) {
                        "$ENV_DOMAIN が未設定。WebFinger の acct とアクターの id に使うので必ず指定すること"
                    }
                    normalized
                }

            val actorUsername: String =
                run {
                    val raw = getenv(ENV_ACTOR_USERNAME)?.trim()
                    if (raw.isNullOrEmpty()) DEFAULT_ACTOR_USERNAME else raw
                }

            val dbPath: Path =
                run {
                    val raw = getenv(ENV_DB_PATH)?.trim()
                    Path.of(if (raw.isNullOrEmpty()) DEFAULT_DB_PATH else raw)
                }

            // PEM は中身をそのまま鍵として読むので、前後の空白も落とさずに渡す
            val actorPrivateKeyPem: String? = getenv(ENV_ACTOR_PRIVATE_KEY_PEM)?.takeIf { it.isNotBlank() }

            val actorPrivateKeyPath: String? = getenv(ENV_ACTOR_PRIVATE_KEY_PATH)?.trim()?.takeIf { it.isNotEmpty() }

            val actorKey: ActorKeyConfig =
                run {
                    // 両方が設定されていたら落とす。片方を黙って無視すると、意図していない鍵で
                    // 起動したことに気付けない。鍵が変わると相手側は署名検証に失敗し続けるうえ、
                    // Mastodon はアクターをキャッシュするので後から直しても戻りが遅い
                    require(actorPrivateKeyPem == null || actorPrivateKeyPath == null) {
                        "$ENV_ACTOR_PRIVATE_KEY_PEM と $ENV_ACTOR_PRIVATE_KEY_PATH は同時に指定できない。どちらか一方にすること"
                    }

                    if (actorPrivateKeyPem != null) {
                        ActorKeyConfig.Pem(actorPrivateKeyPem)
                    } else {
                        ActorKeyConfig.File(Path.of(actorPrivateKeyPath ?: DEFAULT_ACTOR_PRIVATE_KEY_PATH))
                    }
                }

            val staticSrcDir: Path? =
                run {
                    val raw = getenv(ENV_STATIC_SRC_DIR)?.trim()
                    if (raw.isNullOrEmpty()) null else Path.of(raw)
                }

            return AppConfig(
                server =
                    ServerConfig(
                        host = host,
                        port = port,
                        domain = domain,
                        actorUsername = actorUsername,
                    ),
                actorKey = actorKey,
                database = DatabaseConfig(path = dbPath),
                staticFiles = StaticFilesConfig(srcDir = staticSrcDir),
            )
        }
    }
}
