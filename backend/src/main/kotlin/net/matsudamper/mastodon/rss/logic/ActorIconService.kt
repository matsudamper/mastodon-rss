package net.matsudamper.mastodon.rss.logic

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.ktor.http.ContentType
import net.matsudamper.mastodon.rss.actor.ActorIcon
import net.matsudamper.mastodon.rss.actor.ActorIcons
import net.matsudamper.mastodon.rss.feed.HttpUrl
import net.matsudamper.mastodon.rss.feed.IconFetchService
import net.matsudamper.mastodon.rss.repository.AccountRepository
import net.matsudamper.mastodon.rss.repository.FeedIcon
import net.matsudamper.mastodon.rss.repository.FeedIconRepository
import net.matsudamper.mastodon.rss.repository.FeedRepository
import net.matsudamper.mastodon.rss.repository.entity.FeedId

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
    /**
     * 取り直しはフィードごとに 1 本ずつにする。まとめて叩かれると、同じ画像を
     * 人数分取りに行ったうえ、同じ置き場へ同時に書き込む
     */
    private val refreshLocks = ConcurrentHashMap<FeedId, Mutex>()

    override suspend fun find(username: String): ActorIcon? {
        val account = accounts.findByUsername(username) ?: return null
        val feed = feeds.findByAccountId(account.id) ?: return null
        val source = HttpUrl.sanitize(feed.iconUrl, feed.url) ?: return null

        stored(feedId = feed.id, source = source)?.let { return it }

        return refreshLocks.computeIfAbsent(feed.id) { Mutex() }.withLock {
            // 待っている間に他が取り直していれば、それを使う
            stored(feedId = feed.id, source = source)
                ?: refresh(feedId = feed.id, source = source)
        }
    }

    /**
     * 置いてあるもののうち、同じ URL から取っていて期限内のもの。無ければ null
     */
    private fun stored(
        feedId: FeedId,
        source: String,
    ): ActorIcon? {
        val stored = icons.find(feedId) ?: return null
        if (stored.sourceUrl != source) return null

        val cacheFor = Duration.between(Instant.now(), stored.expiresAt)
        if (cacheFor <= Duration.ZERO) return null
        return stored.toActorIcon(cacheFor)
    }

    private suspend fun refresh(
        feedId: FeedId,
        source: String,
    ): ActorIcon? {
        val fetched = fetcher.fetch(source)
        if (fetched !is IconFetchService.FetchResult.Success) {
            // 取り直せなかったときは、期限が切れていても同じ URL から取ったものを出す。
            // 配信元が落ちている間だけアイコンが消えるのは、見ている側からは壊れて見える
            return icons.find(feedId)
                ?.takeIf { it.sourceUrl == source }
                ?.toActorIcon(Duration.ZERO)
        }

        // 毎回取り直せと言われているものは置かない。前に置いたものも残さない
        if (fetched.freshFor == Duration.ZERO) {
            discard(feedId)
            return ActorIcon(bytes = fetched.bytes, contentType = fetched.contentType, cacheFor = Duration.ZERO)
        }

        val freshFor = fetched.freshFor ?: defaultFreshFor
        val now = Instant.now()
        icons.save(
            feedId = feedId,
            icon = FeedIcon(
                sourceUrl = source,
                contentType = fetched.contentType.toString(),
                path = store.write(feedId = feedId, bytes = fetched.bytes),
                fetchedAt = now,
                expiresAt = now.plus(freshFor),
            ),
        )

        return ActorIcon(bytes = fetched.bytes, contentType = fetched.contentType, cacheFor = freshFor)
    }

    private fun discard(feedId: FeedId) {
        val stored = icons.find(feedId) ?: return
        icons.delete(feedId)
        store.delete(stored.path)
    }

    /**
     * 置いてあるものを読む。消えていれば null で、取り直す側に倒れる
     */
    private fun FeedIcon.toActorIcon(cacheFor: Duration): ActorIcon? {
        val bytes = store.read(path) ?: return null
        return ActorIcon(bytes = bytes, contentType = ContentType.parse(contentType), cacheFor = cacheFor)
    }

    private companion object {
        val DEFAULT_FRESH_FOR: Duration = Duration.ofHours(1)
    }
}
