package net.matsudamper.mastodon.rss.logic

import java.time.Instant
import net.matsudamper.mastodon.rss.actor.ActorPublisher
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
    private val actorPublisher: ActorPublisher,
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
     * アカウントを消して、消したことをフォロワーに配る。
     *
     * 配信した投稿とフォロワー、登録したフィードと取り込んだ記事も一緒に消える。
     * 名前で持っているもの（投稿とフォロワー）を残すと、同じ名前で作り直したときに
     * 引き継がれるので、消えるものはこの 1 回で消し切る。
     *
     * アカウントの行を先に消す。消えていればその名前は引き当てられなくなり、
     * 投稿の配信や `Follow` の受理が止まる。後から入った行が消し漏れて、
     * 作り直したアカウントに引き継がれることがなくなる。
     * 消せた 1 つだけが以降に進むので、同時に呼ばれても配信は 1 回になる。
     */
    suspend fun delete(username: String): DeleteResult {
        val account = accounts.findByUsername(username)
            ?: return DeleteResult.Failure(DeleteFailure.UNKNOWN_ACCOUNT)

        if (!accounts.delete(account.id)) {
            return DeleteResult.Failure(DeleteFailure.UNKNOWN_ACCOUNT)
        }

        val deleted = actorPublisher.delete(ActorUrls(domain = domain, username = account.username))

        return DeleteResult.Success(
            username = account.username,
            deletedNotes = deleted.deletedNotes,
            removedFollowers = deleted.removedFollowers,
            deliveryTargets = deleted.targets,
            delivered = deleted.delivered,
        )
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

    sealed interface DeleteResult {
        /**
         * @param username 消したアカウントの名前。保存されていた綴りで返す
         * @param deletedNotes 消した投稿の数
         * @param removedFollowers 外したフォロワーの数
         * @param deliveryTargets `Delete` を送った宛先の数
         * @param delivered そのうち相手が受け取った数
         */
        data class Success(
            val username: String,
            val deletedNotes: Int,
            val removedFollowers: Int,
            val deliveryTargets: Int,
            val delivered: Int,
        ) : DeleteResult

        data class Failure(
            val reason: DeleteFailure,
        ) : DeleteResult
    }

    enum class DeleteFailure {
        UNKNOWN_ACCOUNT,
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
