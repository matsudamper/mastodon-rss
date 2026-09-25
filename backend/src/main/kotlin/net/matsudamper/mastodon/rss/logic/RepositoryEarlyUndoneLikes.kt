package net.matsudamper.mastodon.rss.logic

import java.time.Instant
import net.matsudamper.mastodon.rss.inbox.EarlyUndoneLikes
import net.matsudamper.mastodon.rss.repository.EarlyUndoneLikeRepository

class RepositoryEarlyUndoneLikes(
    private val repository: EarlyUndoneLikeRepository,
) : EarlyUndoneLikes {
    override fun remember(
        actorUri: String,
        activityUri: String,
        expiresAt: Instant,
    ) {
        repository.remember(actorUri = actorUri, activityUri = activityUri, expiresAt = expiresAt)
    }

    override fun isRemembered(
        actorUri: String,
        activityUri: String,
        now: Instant,
    ): Boolean = repository.isRemembered(actorUri = actorUri, activityUri = activityUri, now = now)
}
