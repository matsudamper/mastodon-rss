package net.matsudamper.mastodon.rss.dataloader

import java.util.concurrent.CompletableFuture
import net.matsudamper.mastodon.rss.logic.AccountService
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory

/**
 * アカウントの名前から表示名と説明文を引く。
 *
 * 一覧に並んだアカウントの分を 1 回の問い合わせでまとめる
 */
class AccountProfileDataLoaderDefine(
    private val accountService: AccountService,
) : DataLoaderDefine<String, AccountService.ResolvedProfile> {
    override val key: String = this::class.java.name

    override fun getDataLoader(): DataLoader<String, AccountService.ResolvedProfile> {
        return DataLoaderFactory.newMappedDataLoader { keys, _ ->
            CompletableFuture.supplyAsync {
                accountService.profiles(keys)
            }
        }
    }
}
