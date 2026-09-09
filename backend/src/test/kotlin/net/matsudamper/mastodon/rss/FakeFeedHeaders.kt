package net.matsudamper.mastodon.rss

import net.matsudamper.mastodon.rss.logic.FeedHeaders
import net.matsudamper.mastodon.rss.repository.entity.FeedId

/**
 * 取り込みが渡してきたヘッダー画像の URL を控えるだけ。
 */
class FakeFeedHeaders : FeedHeaders {
    val refreshed: MutableList<Pair<FeedId, String?>> = mutableListOf()

    /**
     * 入れ替わったことにするか。既定は入れ替わらない
     */
    var changed: Boolean = false

    override suspend fun refresh(
        feedId: FeedId,
        headerUrl: String?,
    ): Boolean {
        refreshed += feedId to headerUrl
        return changed
    }
}
