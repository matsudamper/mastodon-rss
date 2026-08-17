package net.matsudamper.mastodon.rss.dataloader

import java.util.concurrent.CompletableFuture
import net.matsudamper.mastodon.rss.actor.ActorDirectory
import net.matsudamper.mastodon.rss.actor.ActorUrls
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory

/**
 * 名前からアカウントを引く。
 *
 * 存在する場合、正規化した名前を返す
 */
class AccountDataLoaderDefine(
    private val directory: ActorDirectory,
) : DataLoaderDefine<String, ActorUrls> {
    override val key: String = this::class.java.name

    override fun getDataLoader(): DataLoader<String, ActorUrls> {
        return DataLoaderFactory.newMappedDataLoader { keys, _ ->
            CompletableFuture.supplyAsync {
                directory.resolve(keys)
            }
        }
    }
}
