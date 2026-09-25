package net.matsudamper.mastodon.rss.inbox

import net.matsudamper.mastodon.rss.actor.ActorUrls

sealed interface InboxRecipient {
    val logLabel: String

    data class Account(
        val urls: ActorUrls,
    ) : InboxRecipient {
        override val logLabel: String = urls.acct
    }

    data object Shared : InboxRecipient {
        override val logLabel: String = "共有 inbox"
    }
}
