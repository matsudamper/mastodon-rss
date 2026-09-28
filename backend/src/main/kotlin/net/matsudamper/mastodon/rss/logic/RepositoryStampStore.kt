package net.matsudamper.mastodon.rss.logic

import net.matsudamper.activitypub.entity.PublicNoteId as MastodonPublicNoteId
import net.matsudamper.activitypub.stamp.StampStore
import net.matsudamper.activitypub.stamp.StampStore.ReceivedStamp
import net.matsudamper.mastodon.rss.repository.NoteStampRepository
import net.matsudamper.mastodon.rss.repository.NoteStampRepository.NewNoteStamp
import net.matsudamper.mastodon.rss.shared.PublicNoteId

class RepositoryStampStore(
    private val stamps: NoteStampRepository,
) : StampStore {
    override fun put(stamp: ReceivedStamp): Boolean = stamps.put(
        NewNoteStamp(
            notePublicId = PublicNoteId(stamp.notePublicId.value),
            actor = StoredRemoteActors.of(stamp.actor),
            emoji = stamp.emoji,
            emojiImageUrl = stamp.emojiImageUrl,
            receivedAt = stamp.receivedAt,
        ),
    )

    override fun remove(
        notePublicId: MastodonPublicNoteId,
        actorUri: String,
        emoji: String,
    ): Boolean = stamps.remove(notePublicId = PublicNoteId(notePublicId.value), actorUri = actorUri, emoji = emoji)

    override fun removeActor(actorUri: String): Int = stamps.removeByActor(actorUri)

    override fun findPublicKeyPem(actorUri: String): String? = stamps.findPublicKeyPem(actorUri)
}
