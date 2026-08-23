package net.matsudamper.mastodon.rss.dataloader

import net.matsudamper.mastodon.rss.graphql.otelSupplyAsync
import net.matsudamper.mastodon.rss.note.NoteStore
import net.matsudamper.mastodon.rss.note.StoredNote
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory

class AccountNoteDataLoaderDefine(
    private val notes: NoteStore,
) : DataLoaderDefine<String, StoredNote> {
    override val key: String = this::class.java.name

    override fun getDataLoader(): DataLoader<String, StoredNote> {
        return DataLoaderFactory.newMappedDataLoader { keys, _ ->
            otelSupplyAsync {
                notes.findByPublicIds(keys.toSet())
            }
        }
    }
}
