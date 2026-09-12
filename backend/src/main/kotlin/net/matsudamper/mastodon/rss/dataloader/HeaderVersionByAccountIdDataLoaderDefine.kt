package net.matsudamper.mastodon.rss.dataloader

import net.matsudamper.mastodon.rss.graphql.otelSupplyAsync
import net.matsudamper.mastodon.rss.logic.FeedHeaderVisibility
import net.matsudamper.mastodon.rss.logic.FeedService
import net.matsudamper.mastodon.rss.repository.FeedHeaderRepository
import net.matsudamper.mastodon.rss.shared.AccountId
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory

/**
 * アカウント ID から、公開画面に出せる取り込み済みヘッダー画像の版を引く。
 *
 * 一覧に並んだアカウントのフィードとヘッダーを、それぞれ 1 回の問い合わせでまとめる。
 */
class HeaderVersionByAccountIdDataLoaderDefine(
    private val feedService: FeedService,
    private val headers: FeedHeaderRepository,
) : DataLoaderDefine<AccountId, String> {
    override val key: String = this::class.java.name

    override fun getDataLoader(): DataLoader<AccountId, String> {
        return DataLoaderFactory.newMappedDataLoader { keys, _ ->
            otelSupplyAsync {
                val feeds = feedService.findByAccountIds(keys.toSet())
                val headersByFeedId = headers.findByFeedIds(feeds.values.map { it.id }.toSet())

                feeds.mapNotNull { (accountId, feed) ->
                    val header = headersByFeedId[feed.id] ?: return@mapNotNull null
                    if (!FeedHeaderVisibility.isPublic(header, feed)) return@mapNotNull null
                    accountId to header.revision
                }.toMap()
            }
        }
    }
}
