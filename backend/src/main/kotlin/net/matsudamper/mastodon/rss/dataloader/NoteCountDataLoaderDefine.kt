package net.matsudamper.mastodon.rss.dataloader

import net.matsudamper.mastodon.rss.graphql.otelSupplyAsync
import net.matsudamper.mastodon.rss.logic.NoteReader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory

/**
 * アカウントの名前から配信した投稿の数を引く。
 *
 * 一覧に並んだアカウントの分を 1 回の問い合わせでまとめる
 */
class NoteCountDataLoaderDefine(
    private val noteReader: NoteReader,
) : DataLoaderDefine<String, Int> {
    override val key: String = this::class.java.name

    override fun getDataLoader(): DataLoader<String, Int> {
        return DataLoaderFactory.newMappedDataLoader { keys, _ ->
            otelSupplyAsync {
                noteReader.noteCounts(keys).mapValues { it.value.toInt() }
            }
        }
    }
}
