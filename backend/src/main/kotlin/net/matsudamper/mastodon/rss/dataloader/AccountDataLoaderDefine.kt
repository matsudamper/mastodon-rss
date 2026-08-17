package net.matsudamper.mastodon.rss.dataloader

import java.util.concurrent.CompletableFuture
import net.matsudamper.mastodon.rss.actor.ActorDirectory
import net.matsudamper.mastodon.rss.actor.ActorUrls
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory

/**
 * 名前からアカウントを引く。
 *
 * 引き当てを [ActorDirectory] に通すのは、WebFinger と Actor が応答する名前と、
 * 画面が「ある」と言う名前を揃えるため。判定が 2 つあると、検索には出るのに
 * 画面が開けない（逆も）という形でずれる。
 *
 * 1 件ずつ引くので、まとめたことによる問い合わせの削減は無い。それでも通すのは、
 * 同じリクエストの中で同じ名前を何度引いても 1 回で済むようにするため。
 */
class AccountDataLoaderDefine(
    private val directory: ActorDirectory,
) : DataLoaderDefine<String, ActorUrls> {
    override val key: String = this::class.java.name

    /**
     * 存在しない名前はキーごと落とす。
     * mapped の DataLoader は返さなかったキーを null で返すので、引いた側で存在しないと分かる
     */
    override fun getDataLoader(): DataLoader<String, ActorUrls> {
        return DataLoaderFactory.newMappedDataLoader { keys, _ ->
            CompletableFuture.supplyAsync {
                keys.mapNotNull { username ->
                    val urls = directory.resolve(username) ?: return@mapNotNull null
                    username to urls
                }.toMap()
            }
        }
    }
}
