package net.matsudamper.mastodon.rss.actor

import java.nio.file.Path

/**
 * アクターの秘密鍵をどこから読むか。
 *
 * `ServerConfig` や `DatabaseConfig` と同じく入口は環境変数に寄せる。
 * 鍵はアクターの同一性そのもので、変わると相手側の署名検証が通らなくなるため、
 * どちらから読んだのかが起動ログから分かるように取得元を型で分けている。
 */
sealed interface ActorKeyConfig {
    /**
     * PEM を環境変数で直接渡す。Kubernetes の Secret や systemd の
     * `EnvironmentFile` から入れる場合はこちら。
     */
    data class Pem(
        val pem: String,
    ) : ActorKeyConfig

    /**
     * PEM をファイルから読む。ファイルが無ければ生成して書き出す。
     * docker compose のようにボリュームを持てる場合はこちら。
     */
    data class File(
        val path: Path,
    ) : ActorKeyConfig

    companion object {
        const val ENV_PRIVATE_KEY_PEM: String = "ACTOR_PRIVATE_KEY_PEM"
        const val ENV_PRIVATE_KEY_PATH: String = "ACTOR_PRIVATE_KEY_PATH"

        const val DEFAULT_PRIVATE_KEY_PATH: String = "./data/actor-private-key.pem"

        fun fromEnvironment(): ActorKeyConfig = from(System::getenv)

        /**
         * 環境変数の読み取り元を差し替えられる形。テストから使う。
         *
         * 両方が設定されていたら落とす。片方を黙って無視すると、意図していない鍵で
         * 起動したことに気付けない。鍵が変わると相手側は署名検証に失敗し続けるうえ、
         * Mastodon はアクターをキャッシュするので後から直しても戻りが遅い。
         */
        internal fun from(getenv: (String) -> String?): ActorKeyConfig {
            val pem = getenv(ENV_PRIVATE_KEY_PEM)?.takeIf { it.isNotBlank() }
            val path = getenv(ENV_PRIVATE_KEY_PATH)?.takeIf { it.isNotBlank() }

            require(pem == null || path == null) {
                "$ENV_PRIVATE_KEY_PEM と $ENV_PRIVATE_KEY_PATH は同時に指定できない。どちらか一方にすること"
            }

            return if (pem != null) {
                Pem(pem)
            } else {
                File(Path.of(path ?: DEFAULT_PRIVATE_KEY_PATH))
            }
        }
    }
}
