package net.matsudamper.mastodon.rss.logic

import net.matsudamper.mastodon.rss.actor.ActorProfile
import net.matsudamper.mastodon.rss.actor.StoredActorProfiles
import net.matsudamper.mastodon.rss.repository.AccountRepository

/**
 * ActivityPub 側の [StoredActorProfiles] を DB に繋ぐ。
 *
 * 毎回引き直す。持ち回すと、編集した後も古い表示名を返し続ける。
 */
class RepositoryActorProfiles(
    private val accounts: AccountRepository,
) : StoredActorProfiles {
    override fun find(username: String): ActorProfile {
        val account = accounts.findByUsername(username) ?: return ActorProfile.EMPTY
        return ActorProfile(displayName = account.displayName, summary = account.summary)
    }
}
