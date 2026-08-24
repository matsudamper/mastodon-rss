package net.matsudamper.mastodon.rss.logic

import java.time.Instant
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.actor.ActorUsernameUtil
import net.matsudamper.mastodon.rss.repository.AccountId
import net.matsudamper.mastodon.rss.repository.AccountProfileRepository
import net.matsudamper.mastodon.rss.repository.AccountRepository
import net.matsudamper.mastodon.rss.repository.FollowerRepository

/**
 * 管理画面から見たアカウントの操作。
 *
 * @param fixed DBに無い組み込みアカウント
 */
class AccountService(
    private val accounts: AccountRepository,
    private val accountProfiles: AccountProfileRepository,
    private val followers: FollowerRepository,
    private val fixed: ActorUrls,
) {
    /**
     * 設定で決まるアカウントを先頭に、追加した順で返す
     */
    fun accounts(): List<ManagedAccount> = buildList {
        add(ManagedAccount(urls = fixed, accountId = null, deletable = false, createdAt = null))

        accounts.list().mapTo(this) { account ->
            ManagedAccount(
                urls = ActorUrls(domain = fixed.domain, username = account.username),
                accountId = account.id,
                deletable = true,
                createdAt = account.createdAt,
            )
        }
    }

    /**
     * 名前で 1 つ引く。応答しない名前なら null
     */
    fun account(username: String): ManagedAccount? {
        if (username.equals(fixed.username, ignoreCase = true)) {
            return ManagedAccount(urls = fixed, accountId = null, deletable = false, createdAt = null)
        }

        val account = accounts.findByUsername(username) ?: return null

        return ManagedAccount(
            urls = ActorUrls(domain = fixed.domain, username = account.username),
            accountId = account.id,
            deletable = true,
            createdAt = account.createdAt,
        )
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

        // 設定で決まるアカウントは DB に無いので、先頭のページにだけ 1 件ぶん割り込ませる
        val head = if (afterUsername == null) {
            listOf(ManagedAccount(urls = fixed, accountId = null, deletable = false, createdAt = null))
        } else {
            emptyList()
        }

        // 設定で決まるアカウントの次は、DB から見れば先頭
        val afterStored = afterUsername?.takeUnless { it.equals(fixed.username, ignoreCase = true) }

        val storedLimit = limit - head.size
        val fetched = accounts.list(afterUsername = afterStored, limit = storedLimit + 1)
        val hasMore = fetched.size > storedLimit

        val page = head + fetched.take(storedLimit).map { account ->
            ManagedAccount(
                urls = ActorUrls(domain = fixed.domain, username = account.username),
                accountId = account.id,
                deletable = true,
                createdAt = account.createdAt,
            )
        }

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
                accountId = added.id,
                deletable = true,
                createdAt = added.createdAt,
            ),
        )
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
        val urls = actorDirectoryUrls(usernames)
        if (urls.isEmpty()) return emptyMap()

        val stored = accountProfiles.findByUsernames(urls.keys)

        return urls.mapValues { (username, actorUrls) ->
            ResolvedProfile(
                displayName = AccountProfileDefaults.displayName(actorUrls.username, stored[username]),
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

    private fun actorDirectoryUrls(usernames: Set<String>): Map<String, ActorUrls> = buildMap {
        for (username in usernames) {
            val managed = account(username) ?: continue
            put(username, managed.urls)
        }
    }

    /**
     * @param deletable 管理画面から消せるか。設定で決まるアカウントは消せない
     * @param createdAt 追加した時刻。設定で決まるアカウントには無い
     */
    data class ManagedAccount(
        val urls: ActorUrls,
        val accountId: AccountId?,
        val deletable: Boolean,
        val createdAt: Instant?,
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
