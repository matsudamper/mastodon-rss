package net.matsudamper.mastodon.rss.repository

import java.nio.file.Path

/**
 * DB ファイルの置き場所。
 *
 * 置き場所をどこから決めるかは呼び出し側の話にする。ライブラリ側で環境変数を
 * 読むと、このモジュールを使うだけで特定の変数名が効いてしまい、
 * テストからも差し替えられない。
 *
 * @param path SQLite の DB ファイルのパス。親ディレクトリは接続時に作られる
 */
data class DatabaseConfig(
    val path: Path,
)
