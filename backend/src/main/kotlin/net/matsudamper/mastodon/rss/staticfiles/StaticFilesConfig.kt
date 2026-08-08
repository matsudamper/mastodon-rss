package net.matsudamper.mastodon.rss.staticfiles

import java.nio.file.Path

/**
 * 配信する静的ファイルの置き場所。
 *
 * `ServerConfig` や `DatabaseConfig` と同じく入口は環境変数に寄せる。
 *
 * `:frontend` の成果物はバイナリに埋め込まない。埋め込むとサーバーのビルドと
 * テストが Kotlin/Wasm のツールチェインに引きずられ、wasm 側が壊れると
 * サーバーのテストも回せなくなるため。実行時にディレクトリを読むだけにする。
 *
 * 管理画面専用の口にはしないので、名前に `ADMIN_` は付けない。
 * フォントのように配信したいファイルはここにまとめて置く。
 *
 * @param srcDir 配信するディレクトリ。未設定なら null で、この場合は何も配信しない
 */
data class StaticFilesConfig(
    val srcDir: Path?,
) {
    companion object {
        const val ENV_STATIC_SRC_DIR: String = "STATIC_SRC_DIR"

        fun fromEnvironment(): StaticFilesConfig = from(System::getenv)

        /**
         * 環境変数の読み取り元を差し替えられる形。テストから使う。
         *
         * 未設定を既定のディレクトリで埋めない。適当な場所を読みに行っても
         * 何も見つからず、設定し忘れなのか置き忘れなのかが分からなくなる。
         */
        internal fun from(getenv: (String) -> String?): StaticFilesConfig {
            val srcDir =
                getenv(ENV_STATIC_SRC_DIR)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { Path.of(it) }

            return StaticFilesConfig(srcDir = srcDir)
        }
    }
}
