package net.matsudamper.mastodon.rss.logic

import java.time.Duration
import java.time.Instant
import net.matsudamper.mastodon.rss.feed.IconFetchService
import net.matsudamper.mastodon.rss.repository.FeedIcon
import net.matsudamper.mastodon.rss.repository.FeedIconRepository
import net.matsudamper.mastodon.rss.repository.entity.FeedId

/**
 * フィードが名乗るアイコンの中身を入れ替える。
 *
 * 取りに行くのはフィードを取り込むときだけにする。見に来たときに取りに行くと、
 * `/users/{name}/icon` は無認証なので叩かれた数だけ外へ出ていくうえ、
 * 配信元が落ちている間は見に来た側がその待ちを被る。
 */
class FeedIconService(
    private val icons: FeedIconRepository,
    private val store: FeedIconStore,
    private val fetcher: IconFetchService,
    private val defaultFreshFor: Duration = DEFAULT_FRESH_FOR,
) : FeedIcons {
    override suspend fun refresh(
        feedId: FeedId,
        iconUrl: String?,
    ) {
        if (iconUrl == null) {
            discard(feedId)
            return
        }

        val fetched = fetcher.fetch(iconUrl)
        if (fetched !is IconFetchService.FetchResult.Success) {
            // 取れなかったときは前のものを残す。配信元が落ちている間だけ
            // アイコンが消えるのは、見ている側からは壊れて見える
            return
        }

        val now = Instant.now()
        icons.save(
            feedId = feedId,
            icon = FeedIcon(
                sourceUrl = iconUrl,
                contentType = fetched.contentType.toString(),
                path = store.write(feedId = feedId, bytes = fetched.bytes),
                fetchedAt = now,
                // 見に来た側に持たせる時間。配信元が持つなと言っていれば持たせない
                expiresAt = now.plus(fetched.freshFor ?: defaultFreshFor),
            ),
        )
    }

    private fun discard(feedId: FeedId) {
        val stored = icons.find(feedId) ?: return
        icons.delete(feedId)
        store.delete(stored.path)
    }

    private companion object {
        val DEFAULT_FRESH_FOR: Duration = Duration.ofHours(1)
    }
}
