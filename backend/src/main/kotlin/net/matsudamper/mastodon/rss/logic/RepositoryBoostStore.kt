package net.matsudamper.mastodon.rss.logic

import net.matsudamper.kotpub.boost.BoostStore
import net.matsudamper.kotpub.boost.BoostStore.ReceivedBoost
import net.matsudamper.kotpub.boost.BoostStore.ReceivedRemoteBoost
import net.matsudamper.mastodon.rss.repository.NoteBoostRepository
import net.matsudamper.mastodon.rss.repository.NoteBoostRepository.NewNoteBoost
import net.matsudamper.mastodon.rss.shared.PublicNoteId

/**
 * こちらの投稿のブーストだけ記録する
 */
class RepositoryBoostStore(
    private val boosts: NoteBoostRepository,
) : BoostStore {
    override fun add(boost: ReceivedBoost): Boolean = boosts.add(
        NewNoteBoost(
            notePublicId = PublicNoteId(boost.notePublicId.value),
            actor = StoredRemoteActors.of(boost.actor),
            activityUri = boost.activityUri,
            receivedAt = boost.receivedAt,
        ),
    )

    override suspend fun saveRemote(boost: ReceivedRemoteBoost) {
        IgnoredBoosts.markIgnored("remote_boost")
    }

    override fun remove(
        actorUri: String,
        activityUri: String,
    ): Boolean = boosts.removeByActivity(actorUri = actorUri, activityUri = activityUri)

    override fun removeActor(actorUri: String): Int = boosts.removeByActor(actorUri)

    override fun findPublicKeyPem(actorUri: String): String? = boosts.findPublicKeyPem(actorUri)
}
