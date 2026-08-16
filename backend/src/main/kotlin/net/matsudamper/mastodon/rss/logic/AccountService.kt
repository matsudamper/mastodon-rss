package net.matsudamper.mastodon.rss.logic

import java.time.Instant
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.actor.ActorUsernameUtil
import net.matsudamper.mastodon.rss.repository.AccountRepository

/**
 * 管理画面から見たアカウントの操作。
 *
 * @param fixed DBに無い組み込みアカウント
 */
class AccountService(
    private val accounts: AccountRepository,
    private val fixed: ActorUrls,
) {
    /**
     * 設定で決まるアカウントを先頭に、追加した順で返す
     */
    fun list(): List<ManagedAccount> = buildList {
        add(ManagedAccount(urls = fixed, deletable = false, createdAt = null))

        accounts.list().mapTo(this) { account ->
            ManagedAccount(
                urls = ActorUrls(domain = fixed.domain, username = account.username),
                deletable = true,
                createdAt = account.createdAt,
            )
        }
    }

    fun add(username: String): AddAccountResult {
        val trimmed = username.trim()

        if (trimmed.isEmpty()) return AddAccountResult.Empty

        val unusable = ActorUsernameUtil.unusableCharacters(trimmed)
        if (unusable.isNotEmpty()) return AddAccountResult.UnusableCharacter(unusable)

        if (trimmed.length > ActorUsernameUtil.MAX_LENGTH) return AddAccountResult.TooLong

        // 設定で決まるアカウントも引き当ての対象なので、名前が埋まっていることに変わりはない
        if (trimmed.equals(fixed.username, ignoreCase = true)) return AddAccountResult.Duplicated

        val added = accounts.add(username = trimmed, createdAt = Instant.now()) ?: return AddAccountResult.Duplicated

        return AddAccountResult.Success(
            ManagedAccount(
                urls = ActorUrls(domain = fixed.domain, username = added.username),
                deletable = true,
                createdAt = added.createdAt,
            ),
        )
    }

    /**
     * @param deletable 管理画面から消せるか。設定で決まるアカウントは消せない
     * @param createdAt 追加した時刻。設定で決まるアカウントには無い
     */
    data class ManagedAccount(
        val urls: ActorUrls,
        val deletable: Boolean,
        val createdAt: Instant?,
    )

    sealed interface AddAccountResult {
        data class Success(
            val account: ManagedAccount,
        ) : AddAccountResult

        /**
         * @param characters 入力に含まれていた使えない文字
         */
        data class UnusableCharacter(
            val characters: List<Char>,
        ) : AddAccountResult

        data object TooLong : AddAccountResult

        data object Empty : AddAccountResult

        data object Duplicated : AddAccountResult
    }
}
