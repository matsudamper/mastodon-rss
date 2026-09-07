package net.matsudamper.mastodon.rss

import net.matsudamper.mastodon.rss.logic.FeedIcons
import net.matsudamper.mastodon.rss.repository.entity.FeedId

/**
 * 取り込みが渡してきたアイコンの URL を控えるだけ
 */
class FakeFeedIcons : FeedIcons {
    val refreshed: MutableList<Pair<FeedId, String?>> = mutableListOf()

    override suspend fun refresh(
        feedId: FeedId,
        iconUrl: String?,
    ) {
        refreshed += feedId to iconUrl
    }
}
