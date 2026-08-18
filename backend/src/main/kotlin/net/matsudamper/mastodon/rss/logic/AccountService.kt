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
    fun accounts(): List<ManagedAccount> = buildList {
        add(ManagedAccount(urls = fixed, deletable = false, createdAt = null))

        accounts.list().mapTo(this) { account ->
            ManagedAccount(
                urls = ActorUrls(domain = fixed.domain, username = account.username),
                deletable = true,
                createdAt = account.createdAt,
            )
        }
    }

    /**
     * カーソルと件数を指定してアカウント一覧を取得する
     */
    fun accounts(cursor: String?, limit: Int): ManagedAccountsPage {
        if (limit <= 0) {
            return ManagedAccountsPage(accounts = emptyList(), hasMore = false, nextCursor = null)
        }

        val fixedAccount = ManagedAccount(urls = fixed, deletable = false, createdAt = null)

        if (cursor == null) {
            if (limit == 1) {
                val dbHasMore = accounts.list(cursor = null, limit = 1).isNotEmpty()
                return ManagedAccountsPage(
                    accounts = listOf(fixedAccount),
                    hasMore = dbHasMore,
                    nextCursor = if (dbHasMore) fixed.username else null,
                )
            }

            val dbLimit = limit - 1
            val dbAccounts = accounts.list(cursor = null, limit = dbLimit + 1)
            val hasMore = dbAccounts.size > dbLimit
            val takenDbAccounts = if (hasMore) dbAccounts.take(dbLimit) else dbAccounts
            val managedList = buildList {
                add(fixedAccount)
                takenDbAccounts.mapTo(this) { account ->
                    ManagedAccount(
                        urls = ActorUrls(domain = fixed.domain, username = account.username),
                        deletable = true,
                        createdAt = account.createdAt,
                    )
                }
            }
            val nextCursor = if (hasMore) {
                managedList.last().urls.username
            } else {
                null
            }
            return ManagedAccountsPage(
                accounts = managedList,
                hasMore = hasMore,
                nextCursor = nextCursor,
            )
        }

        val isFixedCursor = cursor.equals(fixed.username, ignoreCase = true)
        val dbCursor = if (isFixedCursor) null else cursor

        val dbAccounts = accounts.list(cursor = dbCursor, limit = limit + 1)
        val hasMore = dbAccounts.size > limit
        val takenDbAccounts = if (hasMore) dbAccounts.take(limit) else dbAccounts
        val managedList = takenDbAccounts.map { account ->
            ManagedAccount(
                urls = ActorUrls(domain = fixed.domain, username = account.username),
                deletable = true,
                createdAt = account.createdAt,
            )
        }
        val nextCursor = if (hasMore) {
            managedList.last().urls.username
        } else {
            null
        }
        return ManagedAccountsPage(
            accounts = managedList,
            hasMore = hasMore,
            nextCursor = nextCursor,
        )
    }

    fun add(username: String): AddAccountResult {
        val trimmed = username.trim()

        val unusableCharacters = ActorUsernameUtil.unusableCharacters(trimmed)
        val tooShort = trimmed.length < ActorUsernameUtil.MIN_LENGTH
        val tooLong = trimmed.length > ActorUsernameUtil.MAX_LENGTH

        // 名前として通らないうちは重複を見に行かない。DB を引いても結果が変わらない
        if (unusableCharacters.isNotEmpty() || tooShort || tooLong) {
            return AddAccountResult.Failure(
                unusableCharacters = unusableCharacters,
                tooShort = tooShort,
                tooLong = tooLong,
                duplicated = false,
            )
        }

        // 設定で決まるアカウントも引き当ての対象なので、名前が埋まっていることに変わりはない
        val added = if (trimmed.equals(fixed.username, ignoreCase = true)) {
            null
        } else {
            accounts.add(username = trimmed, createdAt = Instant.now())
        }

        if (added == null) {
            return AddAccountResult.Failure(
                unusableCharacters = emptyList(),
                tooShort = false,
                tooLong = false,
                duplicated = true,
            )
        }

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

    data class ManagedAccountsPage(
        val accounts: List<ManagedAccount>,
        val hasMore: Boolean,
        val nextCursor: String?,
    )

    sealed interface AddAccountResult {
        data class Success(
            val account: ManagedAccount,
        ) : AddAccountResult

        /**
         * 通らなかった理由。1 回の入力で複数当てはまることがあるので並べて返す。
         *
         * @param unusableCharacters 入力に含まれていた使えない文字
         * @param tooShort 文字数が下限に足りない
         * @param tooLong 文字数が上限を超えている
         * @param duplicated 同じ名前のアカウントが既にある
         */
        data class Failure(
            val unusableCharacters: List<Char>,
            val tooShort: Boolean,
            val tooLong: Boolean,
            val duplicated: Boolean,
        ) : AddAccountResult
    }
}
