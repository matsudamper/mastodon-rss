package net.matsudamper.mastodon.rss.logic

import net.matsudamper.mastodon.rss.repository.entity.FeedId

/** 取り込みに合わせてプロフィール画像の中身を入れ替える先 */
interface FeedIcons {
    suspend fun refresh(
        feedId: FeedId,
        iconUrl: String?,
    )

    suspend fun refreshHeader(
        feedId: FeedId,
        headerUrl: String?,
    )
}
