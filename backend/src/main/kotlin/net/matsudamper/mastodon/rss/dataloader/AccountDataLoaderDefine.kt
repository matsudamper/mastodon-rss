package net.matsudamper.mastodon.rss.dataloader

import net.matsudamper.mastodon.rss.graphql.otelSupplyAsync
import net.matsudamper.mastodon.rss.logic.AccountService
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory

/**
 * 名前からアカウントを引く。
 *
 * 存在する場合、保存されている綴りと id を返す
 */
class AccountDataLoaderDefine(
    private val accounts: AccountService,
) : DataLoaderDefine<String, AccountService.ManagedAccount> {
    override val key: String = this::class.java.name

    override fun getDataLoader(): DataLoader<String, AccountService.ManagedAccount> {
        return DataLoaderFactory.newMappedDataLoader { keys, _ ->
            otelSupplyAsync {
                accounts.accountsByUsernames(keys)
            }
        }
    }
}
