package net.matsudamper.mastodon.rss.logic

import java.time.Duration
import java.time.Instant
import io.ktor.http.ContentType
import net.matsudamper.mastodon.rss.actor.ActorHeader
import net.matsudamper.mastodon.rss.actor.ActorHeaders
import net.matsudamper.mastodon.rss.repository.AccountRepository
import net.matsudamper.mastodon.rss.repository.FeedHeaderRepository
import net.matsudamper.mastodon.rss.repository.FeedRepository

/**
 * アクターのプロフィールヘッダーを返す。
 *
 * 置いてあるものだけを返し、ここから配信元へは取りに行かない。入れ替えは
 * フィードの取り込みに合わせて [FeedHeaderService] が行う。
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
        if (!FeedHeaderVisibility.isPublic(stored, feed)) return null
        val bytes = store.read(stored.path) ?: return null

        return ActorHeader(
            bytes = bytes,
            contentType = ContentType.parse(stored.contentType),
            // Actor JSON が URL に付けているのと同じ値。一致していれば、
            // その URL は今置いてあるものを指している
            version = stored.revision,
            // 期限までの残りだけ持たせる。取ったときの長さをそのまま渡すと、
            // 期限の間際に来た側は期限を過ぎた後まで前の画像を持つ
            cacheFor = Duration.between(Instant.now(), stored.expiresAt).coerceAtLeast(Duration.ZERO),
        )
    }
}
