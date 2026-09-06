package net.matsudamper.mastodon.rss.repository

import java.time.Instant
import net.matsudamper.mastodon.rss.repository.entity.FeedId

/**
 * 取ってきたアイコンの記録。中身はファイルに置き、ここには置き場と期限だけを持つ。
 *
 * 中身をこちらに持つのは、公開画面が canvas に描く都合で画像をバイト列で読む必要があり、
 * 配信元が CORS を許していないとブラウザからは読めないため。取り直す間隔は
 * 配信元の言い分（`Cache-Control`）で決まるので、期限も一緒に持つ。
 */
interface FeedIconRepository {
    fun find(feedId: FeedId): FeedIcon?

    /** 入れ替える。フィードに 1 つしか持たない */
    fun save(
        feedId: FeedId,
        icon: FeedIcon,
    )

    /** 記録を消す。ファイルの後始末は呼び出し側が行う */
    fun delete(feedId: FeedId)
}

/**
 * @param sourceUrl 取ってきた元の URL。`feeds.icon_url` が変わったかどうかの判断に使う
 * @param path 中身の置き場。置き場のディレクトリから見た相対パス
 * @param expiresAt この時刻を過ぎたら取り直す
 */
data class FeedIcon(
    val sourceUrl: String,
    val contentType: String,
    val path: String,
    val fetchedAt: Instant,
    val expiresAt: Instant,
)
