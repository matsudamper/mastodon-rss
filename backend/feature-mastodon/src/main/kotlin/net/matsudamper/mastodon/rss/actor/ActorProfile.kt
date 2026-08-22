package net.matsudamper.mastodon.rss.actor

/**
 * Actor JSON に載せる表示名と説明文。
 */
data class ActorProfile(
    val name: String,
    val summary: String,
)
