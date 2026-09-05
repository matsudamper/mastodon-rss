package net.matsudamper.mastodon.rss.logic

import java.time.Instant
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.actor.ActorUsernameUtil
import net.matsudamper.mastodon.rss.repository.Account
import net.matsudamper.mastodon.rss.repository.AccountRepository
import net.matsudamper.mastodon.rss.repository.FollowerRepository
import net.matsudamper.mastodon.rss.shared.AccountId

/**
 * 管理画面から見たアカウントの操作。
 */
class AccountService(
    private val accounts: AccountRepository,
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
     * プロフィールを書き換える。空文字は未設定に戻す扱いにする。
     *
     * 入力を空にしたことと、初めから設定していないことを分ける意味が無いので、
     * どちらも同じ「未設定」にまとめる
     */
    fun updateProfile(
        username: String,
        displayName: String,
        summary: String,
    ): UpdateProfileResult {
        val trimmedDisplayName = displayName.trim()
        val trimmedSummary = summary.trim()

        val displayNameTooLong = trimmedDisplayName.length > DISPLAY_NAME_MAX_LENGTH
        val summaryTooLong = trimmedSummary.length > SUMMARY_MAX_LENGTH

        if (displayNameTooLong || summaryTooLong) {
            return UpdateProfileResult.Failure(
                unknownAccount = false,
                displayNameTooLong = displayNameTooLong,
                summaryTooLong = summaryTooLong,
            )
        }

        val account = accounts.findByUsername(username)
            ?: return UpdateProfileResult.Failure(
                unknownAccount = true,
                displayNameTooLong = false,
                summaryTooLong = false,
            )

        val updated = accounts.updateProfile(
            id = account.id,
            displayName = trimmedDisplayName.ifEmpty { null },
            summary = trimmedSummary.ifEmpty { null },
        ) ?: return UpdateProfileResult.Failure(
            unknownAccount = true,
            displayNameTooLong = false,
            summaryTooLong = false,
        )

        return UpdateProfileResult.Success(updated.toManaged())
    }

    private fun Account.toManaged(): ManagedAccount = ManagedAccount(
        urls = ActorUrls(domain = domain, username = username),
        accountId = id,
        createdAt = createdAt,
        displayName = displayName,
        summary = summary,
    )

    /**
     * @param displayName プロフィールの表示名。未設定なら null
     * @param summary プロフィールの説明文。未設定なら null
     */
    data class ManagedAccount(
        val urls: ActorUrls,
        val accountId: AccountId,
        val createdAt: Instant,
        val displayName: String?,
        val summary: String?,
    )

    /**
     * @param nextUsername 続きがある場合の、次に渡す `afterUsername`
     */
    data class ManagedAccountsPage(
        val accounts: List<ManagedAccount>,
        val hasMore: Boolean,
        val nextUsername: String?,
    )

    sealed interface UpdateProfileResult {
        data class Success(
            val account: ManagedAccount,
        ) : UpdateProfileResult

        /**
         * 通らなかった理由。1 回の入力で複数当てはまることがあるので並べて返す。
         *
         * @param unknownAccount その名前のアカウントが無い
         * @param displayNameTooLong 表示名が上限を超えている
         * @param summaryTooLong 説明文が上限を超えている
         */
        data class Failure(
            val unknownAccount: Boolean,
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

    companion object {
        /**
         * Mastodon の表示名の上限に合わせる。長い名前は相手側で切られる
         */
        const val DISPLAY_NAME_MAX_LENGTH: Int = 30

        /**
         * Mastodon のプロフィール説明文の上限に合わせる
         */
        const val SUMMARY_MAX_LENGTH: Int = 500
    }
}
