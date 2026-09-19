package net.matsudamper.mastodon.rss.dataloader

import net.matsudamper.mastodon.rss.graphql.otelSupplyAsync
import net.matsudamper.mastodon.rss.repository.NoteReactionCount
import net.matsudamper.mastodon.rss.repository.NoteReactionRepository
import net.matsudamper.mastodon.rss.shared.PublicNoteId
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory

/**
 * 投稿に届いた反応を引く。
 *
 * 一覧に並んだ投稿の分を 1 回の問い合わせでまとめる。お気に入りもスタンプも
 * 同じ 1 件として届くので、分けずに取ってから画面に出す形にする
 */
class NoteReactionsDataLoaderDefine(
    private val reactions: NoteReactionRepository,
) : DataLoaderDefine<PublicNoteId, List<NoteReactionCount>> {
    override val key: String = this::class.java.name

    override fun getDataLoader(): DataLoader<PublicNoteId, List<NoteReactionCount>> {
        return DataLoaderFactory.newMappedDataLoader { keys, _ ->
            otelSupplyAsync {
                val counts = reactions.countsByNotes(keys.toSet())
                // 1 つも届いていない投稿も 0 件として返す。引き当てから漏らすと
                // DataLoader が null を返し、反応の無い投稿がエラーになる
                keys.associateWith { counts[it].orEmpty() }
            }
        }
    }
}
