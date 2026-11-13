package net.matsudamper.mastodon.rss.dataloader

import net.matsudamper.mastodon.rss.graphql.otelSupplyAsync
import net.matsudamper.mastodon.rss.repository.NoteBoostRepository
import net.matsudamper.mastodon.rss.shared.PublicNoteId
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory

class NoteBoostCountDataLoaderDefine(
    private val boosts: NoteBoostRepository,
) : DataLoaderDefine<PublicNoteId, Int> {
    override val key: String = this::class.java.name

    override fun getDataLoader(): DataLoader<PublicNoteId, Int> {
        return DataLoaderFactory.newMappedDataLoader { keys, _ ->
            otelSupplyAsync {
                val counts = boosts.countsByNotes(keys.toSet())
                // 1 つも届いていない投稿も 0 件として返す。引き当てから漏らすと
                // DataLoader が null を返し、ブーストの無い投稿がエラーになる
                keys.associateWith { counts[it] ?: 0 }
            }
        }
    }
}
