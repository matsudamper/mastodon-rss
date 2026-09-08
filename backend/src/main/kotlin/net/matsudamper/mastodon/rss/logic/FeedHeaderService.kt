package net.matsudamper.mastodon.rss.logic

import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.matsudamper.mastodon.rss.feed.IconFetchService
import net.matsudamper.mastodon.rss.repository.FeedHeader
import net.matsudamper.mastodon.rss.repository.FeedHeaderRepository
import net.matsudamper.mastodon.rss.repository.entity.FeedId

/**
 * フィードや配信元ページが名乗るヘッダー画像の中身を入れ替える。
 *
 * 取得の安全性と画像形式の制限はアイコンと同じ [IconFetchService] に任せる。
 */
class FeedHeaderService(
    private val headers: FeedHeaderRepository,
    private val store: FeedIconStore,
    private val fetcher: IconFetchService,
    private val defaultFreshFor: Duration = DEFAULT_FRESH_FOR,
) : FeedHeaders {
    private val locks = ConcurrentHashMap<FeedId, Mutex>()

    override suspend fun refresh(
        feedId: FeedId,
        headerUrl: String?,
    ) {
        if (headerUrl == null) return

        locks.computeIfAbsent(feedId) { Mutex() }.withLock {
            replace(feedId = feedId, headerUrl = headerUrl)
        }
    }

    private suspend fun replace(
        feedId: FeedId,
        headerUrl: String,
    ) {
        val fetched = fetcher.fetch(headerUrl)
        if (fetched !is IconFetchService.FetchResult.Success) return

        val previous = headers.find(feedId)
        val path = store.write(feedId = feedId, bytes = fetched.bytes)
        val now = Instant.now()

        runCatching {
            headers.save(
                feedId = feedId,
                header = FeedHeader(
                    sourceUrl = headerUrl,
                    contentType = fetched.contentType.toString(),
                    revision = contentRevision(fetched.bytes),
                    path = path,
                    fetchedAt = now,
                    expiresAt = now.plus(fetched.freshFor ?: defaultFreshFor),
                ),
            )
        }.onFailure { error ->
            store.delete(path)
            throw error
        }

        previous?.path?.takeIf { it != path }?.let { store.delete(it) }
    }

    private fun contentRevision(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private companion object {
        val DEFAULT_FRESH_FOR: Duration = Duration.ofDays(1)
    }
}
