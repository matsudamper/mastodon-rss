package net.matsudamper.mastodon.rss.feed

/**
 * 実際に名前を引かせない [ExternalHosts]。
 *
 * 本物を使うと、テストの結果がその環境で名前を引けるかどうかで変わる
 */
object TestExternalHosts : ExternalHosts {
    override suspend fun isExternal(url: String): Boolean = true
}
