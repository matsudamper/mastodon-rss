package net.matsudamper.mastodon.rss.dataloader

import net.matsudamper.mastodon.rss.graphql.otelSupplyAsync
import net.matsudamper.mastodon.rss.repository.NoteFavouriteRepository
import net.matsudamper.mastodon.rss.shared.PublicNoteId
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory

/**
 * 一覧に並んだ投稿の分を 1 回の問い合わせでまとめる
 */
class NoteFavouriteCountDataLoaderDefine(
    private val favourites: NoteFavouriteRepository,
) : DataLoaderDefine<PublicNoteId, Int> {
    override val key: String = this::class.java.name

    override fun getDataLoader(): DataLoader<PublicNoteId, Int> {
        return DataLoaderFactory.newMappedDataLoader { keys, _ ->
            otelSupplyAsync {
                val counts = favourites.countsByNotes(keys.toSet())
                // 1 つも届いていない投稿も 0 件として返す。引き当てから漏らすと
                // DataLoader が null を返し、お気に入りの無い投稿がエラーになる
                keys.associateWith { counts[it] ?: 0 }
            }
        }
    }
}
