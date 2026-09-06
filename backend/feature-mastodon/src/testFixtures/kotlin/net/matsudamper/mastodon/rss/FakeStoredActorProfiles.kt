package net.matsudamper.mastodon.rss

import net.matsudamper.mastodon.rss.actor.ActorProfile
import net.matsudamper.mastodon.rss.actor.StoredActorProfiles

class FakeStoredActorProfiles(
    private val profiles: Map<String, ActorProfile> = mapOf(),
) : StoredActorProfiles {
    override fun find(username: String): ActorProfile {
        return profiles[username] ?: ActorProfile.EMPTY
    }
}
