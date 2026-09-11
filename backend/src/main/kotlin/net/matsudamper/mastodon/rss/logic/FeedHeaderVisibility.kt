package net.matsudamper.mastodon.rss.logic

import net.matsudamper.mastodon.rss.feed.HttpUrl
import net.matsudamper.mastodon.rss.repository.Feed
import net.matsudamper.mastodon.rss.repository.FeedHeader

/**
 * 取り込み済みヘッダーを外に出してよいかの判定。
 *
 * 配信・Actor JSON・GraphQL が同じ判定を使う。別々に持つと、判定を変えたときに
 * URL を出す側と中身を返す側で指す先が食い違う。
 */
object FeedHeaderVisibility {
    /**
     * 取り込みが取ってこない形の URL（http(s) 以外）から入ったものは出さない
     */
    fun isPublic(
        header: FeedHeader,
        feed: Feed,
    ): Boolean = HttpUrl.sanitize(header.sourceUrl, feed.url) != null
}
