package net.matsudamper.mastodon.rss.inbox

import kotlin.time.Duration.Companion.hours
import net.matsudamper.mastodon.rss.actor.ExpiringCache
import net.matsudamper.mastodon.rss.actor.createExpiringCache

/**
 * `Like` より先に届いた `Undo` が指していた id。
 *
 * 配送の順番は保証されず、取り消しが先に届くことがある。覚えておかないと、後から
 * 届いた `Like` だけが残り、相手が取り消したお気に入りが数に入る。Mastodon も同じ理由で
 * 6 時間覚えている。
 *
 * 相手ごとに分けて持つ。id だけで覚えると、他人の `Like` の id を先に送り込んで
 * その相手のお気に入りを弾ける。
 */
class EarlyUndoneLikes {
    private val ids: ExpiringCache<Pair<String, String>, Unit> = createExpiringCache(MAX_ENTRIES)

    fun remember(
        actorUri: String,
        activityUri: String,
    ) {
        ids.put(actorUri to activityUri, Unit, TTL.inWholeMilliseconds)
    }

    fun contains(
        actorUri: String,
        activityUri: String,
    ): Boolean = ids.get(actorUri to activityUri) != null

    private companion object {
        val TTL = 6.hours

        const val MAX_ENTRIES = 10_000
    }
}
