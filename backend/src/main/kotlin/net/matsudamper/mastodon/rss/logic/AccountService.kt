package net.matsudamper.mastodon.rss.logic

import java.time.Instant
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.actor.ActorUsername
import net.matsudamper.mastodon.rss.repository.AccountRepository

/**
 * 管理画面から見たアカウントの操作。
 *
 * @param fixed 設定 (`ACTOR_USERNAME`) で決まるアカウント。DB には無いが応答はするので、
 *   一覧に混ぜて出し、同じ名前の追加を弾く
 */
class AccountService(
    private val accounts: AccountRepository,
    private val fixed: ActorUrls,
) {
    /** 設定で決まるアカウントを先頭に、追加した順で返す */
    fun list(): List<ManagedAccount> =
        buildList {
            add(ManagedAccount(urls = fixed, fromConfig = true, createdAt = null))

            accounts.list().mapTo(this) { account ->
                ManagedAccount(
                    urls = ActorUrls(domain = fixed.domain, username = account.username),
                    fromConfig = false,
                    createdAt = account.createdAt,
                )
            }
        }

    fun add(username: String): AddAccountResult {
        val trimmed = username.trim()

        if (!ActorUsername.isValid(trimmed)) return AddAccountResult.InvalidUsername

        // 設定側が勝つので、同じ名前を入れても引けないアカウントが残るだけになる
        if (trimmed.equals(fixed.username, ignoreCase = true)) return AddAccountResult.ReservedUsername

        val added =
            accounts.add(username = trimmed, createdAt = Instant.now())
                ?: return AddAccountResult.Duplicated

        return AddAccountResult.Success(
            ManagedAccount(
                urls = ActorUrls(domain = fixed.domain, username = added.username),
                fromConfig = false,
                createdAt = added.createdAt,
            ),
        )
    }
}

/**
 * @param fromConfig 設定で決まるアカウントか。管理画面から追加も削除もできない
 * @param createdAt 追加した時刻。設定で決まるアカウントには無い
 */
data class ManagedAccount(
    val urls: ActorUrls,
    val fromConfig: Boolean,
    val createdAt: Instant?,
)

sealed interface AddAccountResult {
    data class Success(
        val account: ManagedAccount,
    ) : AddAccountResult

    data object InvalidUsername : AddAccountResult

    data object ReservedUsername : AddAccountResult

    data object Duplicated : AddAccountResult
}
