package net.matsudamper.mastodon.rss.dataloader

import net.matsudamper.mastodon.rss.graphql.otelSupplyAsync
import net.matsudamper.mastodon.rss.note.NoteStore
import net.matsudamper.mastodon.rss.note.StoredNote
import net.matsudamper.mastodon.rss.shared.PublicNoteId
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory

class AccountNoteDataLoaderDefine(
    private val notes: NoteStore,
) : DataLoaderDefine<PublicNoteId, StoredNote> {
    override val key: String = this::class.java.name

    override fun getDataLoader(): DataLoader<PublicNoteId, StoredNote> {
        return DataLoaderFactory.newMappedDataLoader { keys, _ ->
            otelSupplyAsync {
                notes.findByPublicIds(keys.toSet())
            }
        }
    }
}
