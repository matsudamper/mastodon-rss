package net.matsudamper.mastodon.rss.logic

import net.matsudamper.activitypub.actor.ActorAppearance
import net.matsudamper.activitypub.actor.ActorLink
import net.matsudamper.activitypub.actor.StoredActorAppearances
import net.matsudamper.mastodon.rss.feed.HttpUrl
import net.matsudamper.mastodon.rss.repository.AccountRepository
import net.matsudamper.mastodon.rss.repository.FeedHeaderRepository
import net.matsudamper.mastodon.rss.repository.FeedRepository

/**
 * ActivityPub 側の [StoredActorAppearances] を DB に繋ぐ。
 *
 * プロフィールのリンク集にはフィードが指しているサイトとフィードの URL を並べ、
 * アイコンとヘッダーはフィードから取り込んだものを出す。
 *
 * 毎回引き直す。持ち回すと、フィードの URL を変えた後も古い URL を返し続ける。
 */
class RepositoryActorAppearances(
    private val accounts: AccountRepository,
    private val feeds: FeedRepository,
    private val headers: FeedHeaderRepository,
) : StoredActorAppearances {
    override fun find(username: String): ActorAppearance {
        val account = accounts.findByUsername(username) ?: return ActorAppearance.EMPTY
        val feed = feeds.findByAccountId(account.id) ?: return ActorAppearance.EMPTY
        val header = headers.find(feed.id)?.takeIf { FeedHeaderVisibility.isPublic(it, feed) }

        // 相手のプロフィールに出る外部リンクになるので、http / https 以外は落とす
        val siteUrl = HttpUrl.sanitize(feed.siteUrl, feed.url)
        val feedUrl = HttpUrl.sanitize(feed.url)
        return ActorAppearance(
            links = buildList {
                if (siteUrl != null) add(ActorLink(name = SITE_LINK_NAME, url = siteUrl))
                if (feedUrl != null) add(ActorLink(name = FEED_LINK_NAME, url = feedUrl))
            },
            iconVersion = HttpUrl.sanitize(feed.iconUrl, feed.url)?.let(ActorIconVersion::of),
            headerVersion = header?.revision,
        )
    }

    private companion object {
        const val SITE_LINK_NAME = "サイト"
        const val FEED_LINK_NAME = "フィード"
    }
}
