package net.matsudamper.mastodon.rss.logic

import java.time.Duration
import java.time.Instant
import io.ktor.http.ContentType
import net.matsudamper.mastodon.rss.actor.ActorIcon
import net.matsudamper.mastodon.rss.actor.ActorIcons
import net.matsudamper.mastodon.rss.feed.HttpUrl
import net.matsudamper.mastodon.rss.feed.IconFetchService
import net.matsudamper.mastodon.rss.repository.AccountRepository
import net.matsudamper.mastodon.rss.repository.FeedIcon
import net.matsudamper.mastodon.rss.repository.FeedIconRepository
import net.matsudamper.mastodon.rss.repository.FeedRepository

/**
 * アクターのプロフィール画像を返す。取ってきたものは置いておき、期限が切れるまで使う。
 *
 * `/users/{name}/icon` は無認証で誰でも叩けるので、毎回取りに行くと叩かれた数だけ
 * 配信元へ外向きの取得が出る。期限は配信元の `Cache-Control` に従い、
 * 言ってこなければ [DEFAULT_FRESH_FOR] を使う。
 */
class ActorIconService(
    private val accounts: AccountRepository,
    private val feeds: FeedRepository,
    private val icons: FeedIconRepository,
    private val store: FeedIconStore,
    private val fetcher: IconFetchService,
    private val defaultFreshFor: Duration = DEFAULT_FRESH_FOR,
) : ActorIcons {
    override suspend fun find(username: String): ActorIcon? {
        val account = accounts.findByUsername(username) ?: return null
        val feed = feeds.findByAccountId(account.id) ?: return null
        val source = HttpUrl.sanitize(feed.iconUrl, feed.url) ?: return null

        val stored = icons.find(feed.id)
        val now = Instant.now()
        if (stored != null && stored.sourceUrl == source && stored.expiresAt > now) {
            stored.toActorIcon()?.let { return it }
        }

        val fetched = fetcher.fetch(source)
        if (fetched !is IconFetchService.FetchResult.Success) {
            // 取り直せなかったときは、期限が切れていても持っているものを出す。
            // 配信元が落ちている間だけアイコンが消えるのは、見ている側からは壊れて見える
            return stored?.toActorIcon()
        }

        val path = store.write(feedId = feed.id, bytes = fetched.bytes)
        icons.save(
            feedId = feed.id,
            icon = FeedIcon(
                sourceUrl = source,
                contentType = fetched.contentType.toString(),
                path = path,
                fetchedAt = now,
                expiresAt = now.plus(fetched.freshFor ?: defaultFreshFor),
            ),
        )

        return ActorIcon(bytes = fetched.bytes, contentType = fetched.contentType)
    }

    /**
     * 置いてあるものを読む。消えていれば null で、取り直す側に倒れる
     */
    private fun FeedIcon.toActorIcon(): ActorIcon? {
        val bytes = store.read(path) ?: return null
        return ActorIcon(bytes = bytes, contentType = ContentType.parse(contentType))
    }

    private companion object {
        val DEFAULT_FRESH_FOR: Duration = Duration.ofHours(1)
    }
}
