package net.matsudamper.mastodon.rss.repository

import java.nio.file.Path

/**
 * DB ファイルの置き場所。
 *
 * @param path SQLite の DB ファイルのパス。親ディレクトリは接続時に作られる
 */
data class DatabaseConfig(
    val path: Path,
) {
    companion object {
        const val ENV_DB_PATH: String = "DB_PATH"
        const val DEFAULT_DB_PATH: String = "./data/mastodon-rss.db"

        /**
         * 環境変数 `DB_PATH` から設定を読む。未設定なら [DEFAULT_DB_PATH] を使う。
         *
         * native-image では起動時に環境変数を読む方が設定ファイルより素直なので、
         * 設定の入口は環境変数に寄せる。
         */
        fun fromEnvironment(): DatabaseConfig {
            val path = System.getenv(ENV_DB_PATH)?.takeIf { it.isNotBlank() } ?: DEFAULT_DB_PATH
            return DatabaseConfig(path = Path.of(path))
        }
    }
}
