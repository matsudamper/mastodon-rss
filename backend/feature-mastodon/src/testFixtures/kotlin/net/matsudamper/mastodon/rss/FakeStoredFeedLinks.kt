package net.matsudamper.mastodon.rss

import net.matsudamper.mastodon.rss.actor.FeedLinks
import net.matsudamper.mastodon.rss.actor.StoredFeedLinks

class FakeStoredFeedLinks(
    private val links: Map<String, FeedLinks> = mapOf(),
) : StoredFeedLinks {
    override fun find(username: String): FeedLinks {
        return links[username] ?: FeedLinks.EMPTY
    }
}
