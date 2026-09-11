package net.matsudamper.mastodon.rss.repository

import java.time.Instant
import net.matsudamper.mastodon.rss.repository.entity.FeedId

/**
 * 取ってきたアイコンの記録。
 *
 * 中身は別に置き、ここにはその置き場を指す文字列と時刻だけを持つ。中身をこちらに入れると
 * DB のファイルが画像のぶんだけ膨らむ。置き場をどこにするかは入れる側が決める。
 */
interface FeedIconRepository {
    fun find(feedId: FeedId): FeedIcon?

    /**
     * 入れ替える。フィードに 1 つしか持たない
     */
    fun save(
        feedId: FeedId,
        icon: FeedIcon,
    )

    /**
     * 記録を消す。ファイルの後始末は呼び出し側が行う
     */
    fun delete(feedId: FeedId)
}

/**
 * @param sourceUrl 取ってきた元の URL
 * @param path 中身の置き場を指す文字列
 * @param expiresAt 中身の有効期限
 */
data class FeedIcon(
    val sourceUrl: String,
    val contentType: String,
    val path: String,
    val fetchedAt: Instant,
    val expiresAt: Instant,
)
