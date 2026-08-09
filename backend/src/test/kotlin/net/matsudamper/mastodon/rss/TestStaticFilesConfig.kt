package net.matsudamper.mastodon.rss

import net.matsudamper.mastodon.rss.staticfiles.StaticFilesConfig

/**
 * テストで使う `StaticFilesConfig`。
 *
 * 静的ファイルを見ないテストはこれを渡す。配信そのものは StaticRoutesTest で確認する。
 */
object TestStaticFilesConfig {
    val value: StaticFilesConfig = StaticFilesConfig(srcDir = null)
}
