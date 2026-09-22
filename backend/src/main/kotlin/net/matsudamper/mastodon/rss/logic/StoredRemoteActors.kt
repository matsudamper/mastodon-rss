package net.matsudamper.mastodon.rss.logic

import net.matsudamper.mastodon.rss.actor.RemoteActor
import net.matsudamper.mastodon.rss.actor.RemoteActorProfile
import net.matsudamper.mastodon.rss.repository.NewRemoteActor
import net.matsudamper.mastodon.rss.repository.RemoteActorProfile as StoredRemoteActorProfile

/**
 * ActivityPub 側の相手のアクターを、DB に渡す形に持ち替える。
 *
 * フォローと反応のどちらの経路でも同じ行を作るので、詰め替えも 1 か所に置く。
 */
internal object StoredRemoteActors {
    fun of(actor: RemoteActor): NewRemoteActor = NewRemoteActor(
        actorUri = actor.actorId,
        inbox = actor.inbox,
        sharedInbox = actor.sharedInbox,
        publicKeyPem = actor.publicKeyPem,
        profile = of(actor.profile),
    )

    fun of(profile: RemoteActorProfile): StoredRemoteActorProfile = StoredRemoteActorProfile(
        preferredUsername = profile.preferredUsername,
        displayName = profile.displayName,
        profileUrl = profile.profileUrl,
        iconUrl = profile.iconUrl,
    )
}
