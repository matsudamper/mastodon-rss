package net.matsudamper.mastodon.rss.logic

import net.matsudamper.mastodon.rss.entity.PublicNoteId as MastodonPublicNoteId
import net.matsudamper.mastodon.rss.favourite.FavouriteStore
import net.matsudamper.mastodon.rss.favourite.FavouriteStore.ReceivedFavourite
import net.matsudamper.mastodon.rss.repository.NoteFavouriteRepository
import net.matsudamper.mastodon.rss.repository.NoteFavouriteRepository.NewNoteFavourite
import net.matsudamper.mastodon.rss.shared.PublicNoteId

class RepositoryFavouriteStore(
    private val favourites: NoteFavouriteRepository,
) : FavouriteStore {
    override fun add(favourite: ReceivedFavourite): Boolean = favourites.add(
        NewNoteFavourite(
            notePublicId = PublicNoteId(favourite.notePublicId.value),
            actor = StoredRemoteActors.of(favourite.actor),
            receivedAt = favourite.receivedAt,
        ),
    )

    override fun removeByNote(
        notePublicId: MastodonPublicNoteId,
        actorUri: String,
    ): Boolean = favourites.removeByNote(notePublicId = PublicNoteId(notePublicId.value), actorUri = actorUri)

    override fun removeActor(actorUri: String): Int = favourites.removeByActor(actorUri)

    override fun findPublicKeyPem(actorUri: String): String? = favourites.findPublicKeyPem(actorUri)
}
