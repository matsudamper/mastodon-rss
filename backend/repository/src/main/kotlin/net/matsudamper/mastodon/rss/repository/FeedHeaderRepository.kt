package net.matsudamper.mastodon.rss.repository

import java.time.Instant
import net.matsudamper.mastodon.rss.repository.entity.FeedId

/**
 * 取ってきたヘッダー画像の記録。
 *
 * 中身は別に置き、ここにはその置き場を指す文字列と時刻だけを持つ。中身をこちらに入れると
 * DB のファイルが画像のぶんだけ膨らむ。置き場をどこにするかは入れる側が決める。
 */
interface FeedHeaderRepository {
    fun find(feedId: FeedId): FeedHeader?

    /**
     * 入れ替える。フィードに 1 つしか持たない
     */
    fun save(
        feedId: FeedId,
        header: FeedHeader,
    )

    /**
     * 記録を消す。中身の後始末は呼び出し側が行う
     */
    fun delete(feedId: FeedId)
}

/**
 * @param sourceUrl 取ってきた元の URL
 * @param revision 中身から決まる版。同じ [sourceUrl] のまま中身が変わったことが分かる
 * @param path 中身の置き場を指す文字列
 * @param expiresAt 中身の有効期限
 */
data class FeedHeader(
    val sourceUrl: String,
    val contentType: String,
    val revision: String,
    val path: String,
    val fetchedAt: Instant,
    val expiresAt: Instant,
)
