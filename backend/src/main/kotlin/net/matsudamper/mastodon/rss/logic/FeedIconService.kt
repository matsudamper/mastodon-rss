package net.matsudamper.mastodon.rss.logic

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val headers: FeedHeaders,
    private val defaultFreshFor: Duration = DEFAULT_FRESH_FOR,
) : FeedIcons {
    /**
     * 入れ替えはフィードごとに 1 本ずつにする。定期ポーリングと手動の再取得が
     * 重なると、ファイルと記録の書き込みが入れ違い、記録と中身が食い違う
     */
    private val locks = ConcurrentHashMap<FeedId, Mutex>()

    override suspend fun refresh(
        feedId: FeedId,
        iconUrl: String?,
    ) {
        locks.computeIfAbsent(feedId) { Mutex() }.withLock {
            replace(feedId = feedId, iconUrl = iconUrl)
        }
    }

    override suspend fun refreshHeader(
        feedId: FeedId,
        headerUrl: String?,
    ) {
        headers.refresh(feedId = feedId, headerUrl = headerUrl)
    }

    private suspend fun replace(
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

        val previous = icons.find(feedId)
        val path = store.write(feedId = feedId, bytes = fetched.bytes)
        val now = Instant.now()

        // 書いた中身と種類を 1 つの行として入れ替える。入れ替えられなければ
        // 書いたものを捨てる。中途半端な組み合わせを見せない
        runCatching {
            icons.save(
                feedId = feedId,
                icon = FeedIcon(
                    sourceUrl = iconUrl,
                    contentType = fetched.contentType.toString(),
                    path = path,
                    fetchedAt = now,
                    // 見に来た側に持たせる時間。配信元が持つなと言っていれば持たせない
                    expiresAt = now.plus(fetched.freshFor ?: defaultFreshFor),
                ),
            )
        }.onFailure { error ->
            store.delete(path)
            throw error
        }

        previous?.path?.takeIf { it != path }?.let { store.delete(it) }
    }

    private fun discard(feedId: FeedId) {
        val stored = icons.find(feedId) ?: return
        icons.delete(feedId)
        store.delete(stored.path)
    }

    private companion object {
        /**
         * 配信元が持たせる時間を言ってこなかったときの長さ。
         *
         * 上限と同じにして、言ってきた場合と合わせて min(配信元, 1 日) にする
         */
        val DEFAULT_FRESH_FOR: Duration = Duration.ofDays(1)
    }
}
