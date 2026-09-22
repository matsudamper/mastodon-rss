package net.matsudamper.mastodon.rss.logic

import net.matsudamper.mastodon.rss.entity.PublicNoteId as MastodonPublicNoteId
import net.matsudamper.mastodon.rss.reaction.ReactionStore
import net.matsudamper.mastodon.rss.reaction.ReceivedReaction
import net.matsudamper.mastodon.rss.repository.NewNoteReaction
import net.matsudamper.mastodon.rss.repository.NoteReactionRepository
import net.matsudamper.mastodon.rss.shared.PublicNoteId

/**
 * ActivityPub 側の [ReactionStore] を DB に繋ぐ。
 * [RepositoryNoteStore] と同じく型を持ち替えるだけの層になる。
 */
class RepositoryReactionStore(
    private val reactions: NoteReactionRepository,
) : ReactionStore {
    override fun add(reaction: ReceivedReaction): Boolean = reactions.add(
        NewNoteReaction(
            notePublicId = PublicNoteId(reaction.notePublicId.value),
            actor = StoredRemoteActors.of(reaction.actor),
            activityUri = reaction.activityUri,
            emoji = reaction.emoji,
            emojiImageUrl = reaction.emojiImageUrl,
            receivedAt = reaction.receivedAt,
        ),
    )

    override fun removeByActivityUri(
        actorUri: String,
        activityUri: String,
    ): Boolean = reactions.removeByActivityUri(actorUri = actorUri, activityUri = activityUri)

    override fun removeByEmoji(
        notePublicId: MastodonPublicNoteId,
        actorUri: String,
        emoji: String,
    ): Boolean = reactions.removeByEmoji(
        notePublicId = PublicNoteId(notePublicId.value),
        actorUri = actorUri,
        emoji = emoji,
    )

    override fun removeActor(actorUri: String): Int = reactions.removeByActor(actorUri)

    override fun findPublicKeyPem(actorUri: String): String? = reactions.findPublicKeyPem(actorUri)
}
