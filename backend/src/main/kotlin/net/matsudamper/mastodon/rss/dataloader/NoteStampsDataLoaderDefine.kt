package net.matsudamper.mastodon.rss.dataloader

import net.matsudamper.mastodon.rss.graphql.otelSupplyAsync
import net.matsudamper.mastodon.rss.repository.NoteStampRepository
import net.matsudamper.mastodon.rss.repository.NoteStampRepository.StampCount
import net.matsudamper.mastodon.rss.shared.PublicNoteId
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory

class NoteStampsDataLoaderDefine(
    private val stamps: NoteStampRepository,
) : DataLoaderDefine<PublicNoteId, List<StampCount>> {
    override val key: String = this::class.java.name

    override fun getDataLoader(): DataLoader<PublicNoteId, List<StampCount>> {
        return DataLoaderFactory.newMappedDataLoader { keys, _ ->
            otelSupplyAsync {
                val counts = stamps.countsByNotes(keys.toSet())
                // 1 つも届いていない投稿も空として返す。引き当てから漏らすと
                // DataLoader が null を返し、スタンプの無い投稿がエラーになる
                keys.associateWith { counts[it].orEmpty() }
            }
        }
    }
}
