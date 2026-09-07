package net.matsudamper.mastodon.rss.repository

import java.time.Instant
import net.matsudamper.mastodon.rss.repository.entity.FeedId

/**
 * 取ってきたヘッダー画像の記録。中身はファイルに置き、ここには置き場と期限だけを持つ。
 */
interface FeedHeaderRepository {
    fun find(feedId: FeedId): FeedHeader?

    /** フィードに 1 つだけ持つ形で入れ替える */
    fun save(
        feedId: FeedId,
        header: FeedHeader,
    )

    /** 記録を消す。ファイルの後始末は呼び出し側が行う */
    fun delete(feedId: FeedId)
}

/**
 * @param sourceUrl 取ってきた元の URL。Actor のヘッダー URL の版を決めるためにも使う
 * @param path 中身の置き場。画像キャッシュのディレクトリから見た相対パス
 * @param expiresAt この時刻を過ぎたら取り直す
 */
data class FeedHeader(
    val sourceUrl: String,
    val contentType: String,
    val path: String,
    val fetchedAt: Instant,
    val expiresAt: Instant,
)
