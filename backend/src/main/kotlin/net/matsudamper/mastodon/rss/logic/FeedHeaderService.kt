package net.matsudamper.mastodon.rss.logic

import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.matsudamper.mastodon.rss.feed.IconFetchService
import net.matsudamper.mastodon.rss.feed.IconImageType
import net.matsudamper.mastodon.rss.repository.FeedHeader
import net.matsudamper.mastodon.rss.repository.FeedHeaderRepository
import net.matsudamper.mastodon.rss.repository.entity.FeedId

/**
 * フィードや配信元ページが名乗るヘッダー画像の中身を入れ替える。
 *
 * 取得の安全性はアイコンと同じ [IconFetchService] に任せ、配る形式はヘッダー用に更に絞る。
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
        val previous = headers.find(feedId)
        if (previous.isReusableFor(headerUrl)) return

        val fetched = fetcher.fetch(headerUrl)
        if (fetched !is IconFetchService.FetchResult.Success) return
        if (fetched.imageType !in ALLOWED_IMAGE_TYPES) return

        val path = store.write(
            feedId = feedId,
            bytes = fetched.bytes,
            imageType = fetched.imageType,
        )
        val now = Instant.now()

        runCatching {
            headers.save(
                feedId = feedId,
                header = FeedHeader(
                    sourceUrl = headerUrl,
                    contentType = fetched.imageType.contentType.toString(),
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

    /**
     * 取り直さずに前のヘッダーをそのまま使えるか。
     *
     * 取り込みのたびに取りに行くと、配信元が言う期限を無視して大きな画像を毎回落とす
     */
    private fun FeedHeader?.isReusableFor(headerUrl: String): Boolean {
        val header = this ?: return false
        if (header.sourceUrl != headerUrl) return false
        if (!header.expiresAt.isAfter(Instant.now())) return false
        return store.exists(header.path)
    }

    private fun contentRevision(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private companion object {
        val DEFAULT_FRESH_FOR: Duration = Duration.ofDays(1)

        /**
         * ヘッダーとして配る種類。アイコンと違い ICO は入れない。
         *
         * `/users/{name}/header` が返す形式は外部仕様で png / jpeg / gif / webp に限っている
         */
        val ALLOWED_IMAGE_TYPES = setOf(
            IconImageType.PNG,
            IconImageType.JPEG,
            IconImageType.GIF,
            IconImageType.WEBP,
        )
    }
}
