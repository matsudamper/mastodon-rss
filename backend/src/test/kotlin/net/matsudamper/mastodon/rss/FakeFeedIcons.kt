package net.matsudamper.mastodon.rss

import net.matsudamper.mastodon.rss.logic.FeedIcons
import net.matsudamper.mastodon.rss.repository.entity.FeedId

/**
 * 取り込みが渡してきたアイコンの URL を控えるだけ
 */
class FakeFeedIcons : FeedIcons {
    val refreshed: MutableList<Pair<FeedId, String?>> = mutableListOf()

    /**
     * 入れ替わったことにするか。既定は入れ替わらない
     */
    var changed: Boolean = false

    override suspend fun refresh(
        feedId: FeedId,
        iconUrl: String?,
    ): Boolean {
        refreshed += feedId to iconUrl
        return changed
    }
}
