package net.matsudamper.mastodon.rss

import net.matsudamper.mastodon.rss.actor.StoredActorNames

class FakeStoredActorNames(
    private val storedUserName: String,
) : StoredActorNames {
    override fun find(username: String): String? {
        return storedUserName.takeIf { it.equals(username, ignoreCase = true) }
    }

    override fun finds(usernames: Set<String>): Map<String, String> {
        return buildMap {
            for (username in usernames) {
                put(username, storedUserName)
            }
        }
    }
}
