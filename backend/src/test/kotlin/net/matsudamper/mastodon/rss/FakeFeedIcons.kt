package net.matsudamper.mastodon.rss

import net.matsudamper.mastodon.rss.logic.FeedIcons
import net.matsudamper.mastodon.rss.repository.entity.FeedId

/** 取り込みが渡してきたプロフィール画像の URL を控えるだけ */
class FakeFeedIcons : FeedIcons {
    val refreshed: MutableList<Pair<FeedId, String?>> = mutableListOf()
    val refreshedHeaders: MutableList<Pair<FeedId, String?>> = mutableListOf()

    override suspend fun refresh(
        feedId: FeedId,
        iconUrl: String?,
    ) {
        refreshed += feedId to iconUrl
    }

    override suspend fun refreshHeader(
        feedId: FeedId,
        headerUrl: String?,
    ) {
        refreshedHeaders += feedId to headerUrl
    }
}
