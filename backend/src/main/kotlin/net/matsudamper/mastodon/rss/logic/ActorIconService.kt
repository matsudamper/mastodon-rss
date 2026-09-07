package net.matsudamper.mastodon.rss.logic

import java.time.Duration
import io.ktor.http.ContentType
import net.matsudamper.mastodon.rss.actor.ActorIcon
import net.matsudamper.mastodon.rss.actor.ActorIcons
import net.matsudamper.mastodon.rss.feed.HttpUrl
import net.matsudamper.mastodon.rss.repository.AccountRepository
import net.matsudamper.mastodon.rss.repository.FeedIconRepository
import net.matsudamper.mastodon.rss.repository.FeedRepository

/**
 * アクターのプロフィール画像を返す。
 *
 * 置いてあるものだけを返し、ここから配信元へは取りに行かない。入れ替えは
 * フィードの取り込みに合わせて [FeedIconService] が行う。
 */
class ActorIconService(
    private val accounts: AccountRepository,
    private val feeds: FeedRepository,
    private val icons: FeedIconRepository,
    private val store: FeedIconStore,
) : ActorIcons {
    override suspend fun find(username: String): ActorIcon? {
        val account = accounts.findByUsername(username) ?: return null
        val feed = feeds.findByAccountId(account.id) ?: return null
        val source = HttpUrl.sanitize(feed.iconUrl, feed.url) ?: return null

        // 取り込みが入れ替える前に URL だけ変わっていることがある。その間は
        // 前のものを出す。出さないと、取り直しに失敗している間アイコンが消える
        val stored = icons.find(feed.id) ?: return null
        val bytes = store.read(stored.path) ?: return null

        return ActorIcon(
            bytes = bytes,
            contentType = ContentType.parse(stored.contentType),
            // 置いてあるものは期限を過ぎていても出す。取り直すかどうかは
            // 取り込みの側で決めるので、ここで期限を見ると出せるものを出さなくなる。
            // 見に来た側に持たせる時間は、配信元が取得時に言ってきた長さをそのまま渡す
            cacheFor = Duration.between(stored.fetchedAt, stored.expiresAt).coerceAtLeast(Duration.ZERO),
        )
    }
}
