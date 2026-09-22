package net.matsudamper.mastodon.rss.dataloader

import net.matsudamper.mastodon.rss.entity.PublicNoteId as MastodonPublicNoteId
import net.matsudamper.mastodon.rss.graphql.otelSupplyAsync
import net.matsudamper.mastodon.rss.logic.AccountService
import net.matsudamper.mastodon.rss.note.NoteStore
import net.matsudamper.mastodon.rss.shared.PublicNoteId
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory

/**
 * 投稿の公開 id から、投稿したアカウントを引く。
 *
 * 投稿を引いてから名前でアカウントを引く 2 段の DataLoader にすると、
 * 1 段目の結果を待ってから 2 段目に載せることになり、dispatch のタイミングが
 * 合わずに待ち続ける。1 つの batch の中で両方を引く
 */
class AccountByNoteDataLoaderDefine(
    private val notes: NoteStore,
    private val accounts: AccountService,
) : DataLoaderDefine<PublicNoteId, AccountService.ManagedAccount> {
    override val key: String = this::class.java.name

    override fun getDataLoader(): DataLoader<PublicNoteId, AccountService.ManagedAccount> {
        return DataLoaderFactory.newMappedDataLoader { keys, _ ->
            otelSupplyAsync {
                val usernameByNote = notes
                    .findByPublicIds(keys.map { MastodonPublicNoteId(it.value) }.toSet())
                    .map { (publicId, note) -> PublicNoteId(publicId.value) to note.username }
                    .toMap()
                val accountByUsername = accounts.accountsByUsernames(usernameByNote.values.toSet())

                usernameByNote.mapNotNull { (publicId, username) ->
                    accountByUsername[username]?.let { publicId to it }
                }.toMap()
            }
        }
    }
}
