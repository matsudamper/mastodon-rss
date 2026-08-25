package net.matsudamper.mastodon.rss.logic

import java.time.Instant
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.actor.ActorUsernameUtil
import net.matsudamper.mastodon.rss.repository.Account
import net.matsudamper.mastodon.rss.repository.AccountId
import net.matsudamper.mastodon.rss.repository.AccountProfileRepository
import net.matsudamper.mastodon.rss.repository.AccountRepository
import net.matsudamper.mastodon.rss.repository.FollowerRepository

/**
 * 管理画面から見たアカウントの操作。
 */
class AccountService(
    private val accounts: AccountRepository,
    private val accountProfiles: AccountProfileRepository,
    private val followers: FollowerRepository,
    private val domain: String,
) {
    /**
     * 追加した順で返す
     */
    fun accounts(): List<ManagedAccount> = accounts.list().map { it.toManaged() }

    /**
     * 名前で 1 つ引く。応答しない名前なら null
     */
    fun account(username: String): ManagedAccount? = accounts.findByUsername(username)?.toManaged()

    /**
     * 名前でまとめて引く。返すマップのキーは渡された名前
     */
    fun accountsByUsernames(usernames: Set<String>): Map<String, ManagedAccount> {
        if (usernames.isEmpty()) return emptyMap()
        return accounts.findByUsernames(usernames).mapValues { it.value.toManaged() }
    }

    /**
     * フォロワーの数をまとめて数える。一覧に並べる分を 1 回で引くために口を分けている
     */
    fun followerCounts(usernames: Set<String>): Map<String, Long> = followers.counts(usernames)

    /**
     * 追加した順で `afterUsername` の次から `limit` 件返す
     */
    fun accounts(afterUsername: String?, limit: Int): ManagedAccountsPage {
        if (limit <= 0) {
            return ManagedAccountsPage(accounts = emptyList(), hasMore = false, nextUsername = null)
        }

        val fetched = accounts.list(afterUsername = afterUsername, limit = limit + 1)
        val hasMore = fetched.size > limit
        val page = fetched.take(limit).map { it.toManaged() }

        return ManagedAccountsPage(
            accounts = page,
            hasMore = hasMore,
            nextUsername = if (hasMore) page.last().urls.username else null,
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

        val added = accounts.add(username = trimmed, createdAt = Instant.now())

        if (added == null) {
            return AddAccountResult.Failure(
                unusableCharacters = emptyList(),
                tooShort = false,
                tooLong = false,
                duplicated = true,
            )
        }

        return AddAccountResult.Success(added.toManaged())
    }

    /**
     * 表示名と説明文を返す。アカウントが無ければ null
     */
    fun profile(username: String): ResolvedProfile? {
        val managed = account(username) ?: return null
        val stored = accountProfiles.findByUsername(managed.urls.username)

        return ResolvedProfile(
            displayName = AccountProfileDefaults.displayName(managed.urls.username, stored),
            summary = AccountProfileDefaults.summary(stored),
            stored = stored != null,
        )
    }

    /**
     * 複数アカウントのプロフィールをまとめて返す。存在しない名前は含めない
     */
    fun profiles(usernames: Set<String>): Map<String, ResolvedProfile> {
        val managed = accountsByUsernames(usernames)
        if (managed.isEmpty()) return emptyMap()

        val stored = accountProfiles.findByUsernames(managed.keys)

        return managed.mapValues { (username, account) ->
            ResolvedProfile(
                displayName = AccountProfileDefaults.displayName(account.urls.username, stored[username]),
                summary = AccountProfileDefaults.summary(stored[username]),
                stored = stored[username] != null,
            )
        }
    }

    fun updateProfile(
        username: String,
        displayName: String,
        summary: String,
    ): UpdateProfileResult {
        val managed = account(username) ?: return UpdateProfileResult.Failure(
            unknownAccount = true,
            emptyDisplayName = false,
            displayNameTooLong = false,
            summaryTooLong = false,
        )

        val trimmedDisplayName = displayName.trim()
        val trimmedSummary = summary.trim()

        if (trimmedDisplayName.isEmpty()) {
            return UpdateProfileResult.Failure(
                unknownAccount = false,
                emptyDisplayName = true,
                displayNameTooLong = false,
                summaryTooLong = false,
            )
        }

        if (trimmedDisplayName.length > AccountProfileDefaults.DISPLAY_NAME_MAX_LENGTH) {
            return UpdateProfileResult.Failure(
                unknownAccount = false,
                emptyDisplayName = false,
                displayNameTooLong = true,
                summaryTooLong = false,
            )
        }

        if (trimmedSummary.length > AccountProfileDefaults.SUMMARY_MAX_LENGTH) {
            return UpdateProfileResult.Failure(
                unknownAccount = false,
                emptyDisplayName = false,
                displayNameTooLong = false,
                summaryTooLong = true,
            )
        }

        accountProfiles.upsert(
            username = managed.urls.username,
            displayName = trimmedDisplayName,
            summary = trimmedSummary,
        )

        return UpdateProfileResult.Success(managed)
    }

    private fun Account.toManaged(): ManagedAccount = ManagedAccount(
        urls = ActorUrls(domain = domain, username = username),
        accountId = id,
        createdAt = createdAt,
    )

    data class ManagedAccount(
        val urls: ActorUrls,
        val accountId: AccountId,
        val createdAt: Instant,
    )

    /**
     * @param nextUsername 続きがある場合の、次に渡す `afterUsername`
     */
    data class ManagedAccountsPage(
        val accounts: List<ManagedAccount>,
        val hasMore: Boolean,
        val nextUsername: String?,
    )

    data class ResolvedProfile(
        val displayName: String,
        val summary: String,
        val stored: Boolean,
    )

    sealed interface UpdateProfileResult {
        data class Success(
            val account: ManagedAccount,
        ) : UpdateProfileResult

        data class Failure(
            val unknownAccount: Boolean,
            val emptyDisplayName: Boolean,
            val displayNameTooLong: Boolean,
            val summaryTooLong: Boolean,
        ) : UpdateProfileResult
    }

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
