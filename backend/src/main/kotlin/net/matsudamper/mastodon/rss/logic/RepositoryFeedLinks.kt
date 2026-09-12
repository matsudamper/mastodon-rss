package net.matsudamper.mastodon.rss.logic

import net.matsudamper.mastodon.rss.actor.FeedLinks
import net.matsudamper.mastodon.rss.actor.StoredFeedLinks
import net.matsudamper.mastodon.rss.feed.HttpUrl
import net.matsudamper.mastodon.rss.repository.AccountRepository
import net.matsudamper.mastodon.rss.repository.FeedHeaderRepository
import net.matsudamper.mastodon.rss.repository.FeedRepository

/**
 * ActivityPub 側の [StoredFeedLinks] を DB に繋ぐ。
 *
 * 毎回引き直す。持ち回すと、フィードの URL を変えた後も古い URL を返し続ける。
 */
class RepositoryFeedLinks(
    private val accounts: AccountRepository,
    private val feeds: FeedRepository,
    private val headers: FeedHeaderRepository,
) : StoredFeedLinks {
    override fun find(username: String): FeedLinks {
        val account = accounts.findByUsername(username) ?: return FeedLinks.EMPTY
        val feed = feeds.findByAccountId(account.id) ?: return FeedLinks.EMPTY
        val header = headers.find(feed.id)?.takeIf { FeedHeaderVisibility.isPublic(it, feed) }

        // 相手のプロフィールに出る外部リンクになるので、http / https 以外は落とす
        return FeedLinks(
            siteUrl = HttpUrl.sanitize(feed.siteUrl, feed.url),
            feedUrl = HttpUrl.sanitize(feed.url),
            iconUrl = HttpUrl.sanitize(feed.iconUrl, feed.url),
            headerVersion = header?.revision,
        )
    }
}
