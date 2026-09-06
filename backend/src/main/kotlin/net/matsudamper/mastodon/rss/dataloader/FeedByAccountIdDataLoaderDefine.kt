package net.matsudamper.mastodon.rss.dataloader

import net.matsudamper.mastodon.rss.graphql.otelSupplyAsync
import net.matsudamper.mastodon.rss.logic.FeedService
import net.matsudamper.mastodon.rss.repository.Feed
import net.matsudamper.mastodon.rss.shared.AccountId
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory

/**
 * アカウント ID から登録済みのフィードを引く。
 *
 * 一覧に並んだアカウントの分を 1 回の問い合わせでまとめる
 */
class FeedByAccountIdDataLoaderDefine(
    private val feedService: FeedService,
) : DataLoaderDefine<AccountId, Feed> {
    override val key: String = this::class.java.name

    override fun getDataLoader(): DataLoader<AccountId, Feed> {
        return DataLoaderFactory.newMappedDataLoader { keys, _ ->
            otelSupplyAsync {
                feedService.findByAccountIds(keys.toSet())
            }
        }
    }
}
