package net.matsudamper.mastodon.rss.dataloader

import net.matsudamper.mastodon.rss.graphql.otelSupplyAsync
import net.matsudamper.mastodon.rss.logic.FeedService
import net.matsudamper.mastodon.rss.repository.FeedItem
import net.matsudamper.mastodon.rss.shared.NoteId
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory

/**
 * 投稿から、その投稿の元になった取り込み済みの記事を引く。
 *
 * 手で書いた投稿と、記事を消した後の投稿には無いので、その分は返らない
 */
class FeedItemByNoteDataLoaderDefine(
    private val feeds: FeedService,
) : DataLoaderDefine<NoteId, FeedItem> {
    override val key: String = this::class.java.name

    override fun getDataLoader(): DataLoader<NoteId, FeedItem> {
        return DataLoaderFactory.newMappedDataLoader { keys, _ ->
            otelSupplyAsync {
                feeds.itemsByNoteIds(keys.map { it.value })
                    .mapKeys { (noteId, _) -> NoteId(noteId) }
            }
        }
    }
}
