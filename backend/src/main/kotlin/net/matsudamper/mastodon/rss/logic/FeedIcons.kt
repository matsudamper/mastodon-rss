package net.matsudamper.mastodon.rss.logic

import net.matsudamper.mastodon.rss.repository.entity.FeedId

/**
 * 取り込みに合わせてアイコンの中身を入れ替える先。
 *
 * 取り込む側は、フィードが今どの URL を名乗っているかだけを渡す。
 */
interface FeedIcons {
    /**
     * @param iconUrl フィードが名乗っているアイコン。名乗っていなければ null で、置いてあるものを消す
     */
    suspend fun refresh(
        feedId: FeedId,
        iconUrl: String?,
    )
}
