package net.matsudamper.mastodon.rss.staticfiles

import java.nio.file.Path

/**
 * 配信する静的ファイルの置き場所。値は
 * [net.matsudamper.mastodon.rss.AppConfig] が環境変数から組み立てて渡す。
 *
 * `:frontend` の成果物はバイナリに埋め込まない。埋め込むとサーバーのビルドと
 * テストが Kotlin/Wasm のツールチェインに引きずられ、wasm 側が壊れると
 * サーバーのテストも回せなくなるため。実行時にディレクトリを読むだけにする。
 *
 * @param srcDir 配信するディレクトリ。未設定なら null で、この場合は何も配信しない
 */
data class StaticFilesConfig(
    val srcDir: Path?,
)
