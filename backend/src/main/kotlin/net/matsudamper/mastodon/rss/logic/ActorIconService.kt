package net.matsudamper.mastodon.rss.logic

import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.matsudamper.mastodon.rss.actor.ActorIcon
import net.matsudamper.mastodon.rss.actor.ActorIcons
import net.matsudamper.mastodon.rss.actor.StoredFeedLinks
import net.matsudamper.mastodon.rss.feed.IconFetchService

/**
 * アクターのプロフィール画像を返す。取ってきたものはしばらく持っておく。
 *
 * `/users/{name}/icon` は無認証で誰でも叩けるので、持たずに毎回取りに行くと、
 * 叩かれた数だけ配信元へ外向きの取得が出る。呼ぶ側に `Cache-Control` を返しても、
 * 守るかどうかは相手次第で、こちらの外向きの数は減らない。
 *
 * 取れなかったことも短く持つ。落ちている配信元に繰り返し取りに行かないため。
 */
class ActorIconService(
    private val feedLinks: StoredFeedLinks,
    private val icons: IconFetchService,
    private val successTtl: Duration = SUCCESS_TTL,
    private val failureTtl: Duration = FAILURE_TTL,
) : ActorIcons {
    private val mutex = Mutex()
    private val cached = mutableMapOf<String, Entry>()

    override suspend fun find(username: String): ActorIcon? {
        val source = feedLinks.find(username).iconUrl ?: return null
        val now = Instant.now()

        // 取得元が変わったら持っているものは使わない。フィードを差し替えても
        // URL が変わらないぶん、中身の切り替えはここでしか起きない
        readCache(username = username, source = source, now = now)?.let { return it.icon }

        val icon = when (val fetched = icons.fetch(source)) {
            is IconFetchService.FetchResult.Success ->
                ActorIcon(bytes = fetched.bytes, contentType = fetched.contentType)

            IconFetchService.FetchResult.Failure -> null
        }

        writeCache(username = username, source = source, icon = icon, now = Instant.now())
        return icon
    }

    private suspend fun readCache(
        username: String,
        source: String,
        now: Instant,
    ): Entry? = mutex.withLock {
        val entry = cached[username] ?: return null
        if (entry.source != source || entry.expiresAt <= now) {
            cached.remove(username)
            return null
        }
        entry
    }

    private suspend fun writeCache(
        username: String,
        source: String,
        icon: ActorIcon?,
        now: Instant,
    ) {
        mutex.withLock {
            // アカウントの数だけ最大 1MB を抱えることになるので、上限を超えたら
            // 古い順に落とす。落ちたぶんは次に呼ばれたときに取り直す
            if (cached.size >= MAX_ENTRIES) {
                cached.entries
                    .sortedBy { it.value.expiresAt }
                    .take(cached.size - MAX_ENTRIES + 1)
                    .forEach { cached.remove(it.key) }
            }

            cached[username] = Entry(
                source = source,
                icon = icon,
                expiresAt = now.plus(if (icon == null) failureTtl else successTtl),
            )
        }
    }

    /**
     * @param source 取得元の URL。ここが変わっていたら持っているものは捨てる
     * @param icon 取れなかった場合は null。取れなかったことも持っておく
     */
    private class Entry(
        val source: String,
        val icon: ActorIcon?,
        val expiresAt: Instant,
    )

    private companion object {
        val SUCCESS_TTL: Duration = Duration.ofMinutes(30)
        val FAILURE_TTL: Duration = Duration.ofMinutes(5)
        const val MAX_ENTRIES = 32
    }
}
