package net.matsudamper.mastodon.rss.repository

import java.time.Instant

interface EarlyUndoneLikeRepository {
    /**
     * 同じ組を覚え直したときは期限を延ばす
     */
    fun remember(
        actorUri: String,
        activityUri: String,
        expiresAt: Instant,
    )

    fun isRemembered(
        actorUri: String,
        activityUri: String,
        now: Instant,
    ): Boolean
}
