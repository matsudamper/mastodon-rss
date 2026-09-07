package net.matsudamper.mastodon.rss.logic

import net.matsudamper.mastodon.rss.repository.entity.FeedId

/** フィードが名乗るヘッダー画像を取り込む口 */
interface FeedHeaders {
    /**
     * @param headerUrl フィードや配信元ページから読めたヘッダー画像。null の場合は今のものを残す
     */
    suspend fun refresh(
        feedId: FeedId,
        headerUrl: String?,
    )
}
