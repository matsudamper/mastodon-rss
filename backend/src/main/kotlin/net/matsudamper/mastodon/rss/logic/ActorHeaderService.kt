package net.matsudamper.mastodon.rss.logic

import java.time.Duration
import java.time.Instant
import io.ktor.http.ContentType
import net.matsudamper.mastodon.rss.actor.ActorHeader
import net.matsudamper.mastodon.rss.actor.ActorHeaders
import net.matsudamper.mastodon.rss.feed.HttpUrl
import net.matsudamper.mastodon.rss.repository.AccountRepository
import net.matsudamper.mastodon.rss.repository.FeedHeaderRepository
import net.matsudamper.mastodon.rss.repository.FeedRepository

/**
 * アクターのプロフィールヘッダーとして、取り込み済みの画像だけを返す。
 */
class ActorHeaderService(
    private val accounts: AccountRepository,
    private val feeds: FeedRepository,
    private val headers: FeedHeaderRepository,
    private val store: FeedIconStore,
) : ActorHeaders {
    override suspend fun find(username: String): ActorHeader? {
        val account = accounts.findByUsername(username) ?: return null
        val feed = feeds.findByAccountId(account.id) ?: return null
        val stored = headers.find(feed.id) ?: return null
        if (HttpUrl.sanitize(stored.sourceUrl, feed.url) == null) return null
        val bytes = store.read(stored.path) ?: return null

        return ActorHeader(
            bytes = bytes,
            contentType = ContentType.parse(stored.contentType),
            version = stored.revision,
            cacheFor = Duration.between(Instant.now(), stored.expiresAt).coerceAtLeast(Duration.ZERO),
        )
    }
}
