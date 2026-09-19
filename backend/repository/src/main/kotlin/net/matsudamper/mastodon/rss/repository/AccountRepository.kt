package net.matsudamper.mastodon.rss.repository

import java.time.Instant
import net.matsudamper.mastodon.rss.shared.AccountId

/**
 * 応答するアカウントの読み書き。
 *
 * 名前は大文字小文字を区別せずに一意にする。区別して持てると同じ acct を指す行が
 * 2 つ並び、どちらを返すかが引き方で変わる。
 *
 * 消したアカウントは行を残して [Account.deletedAt] を入れる。読み出しは
 * [findDeletedByUsername] を除いて生きているものだけを返すので、外から見ると消えている。
 * 行を残すのは、消えたアカウントとして署名する `Delete{Actor}` を送り切るため。
 */
interface AccountRepository {
    /**
     * 追加した順に全件返す。
     *
     * 呼び出しを `list(after, limit)` に移し切ったら消す。
     * アカウントが増えるほど 1 回の応答が重くなり、上限も置けない
     */
    @Deprecated("ページングに移行する。list(after, limit) を使う")
    fun list(): List<Account>

    /**
     * 追加した順で [after] の次から [limit] 件返す。
     *
     * @param after ここより後ろを返す。null なら先頭から
     */
    fun list(after: AccountPosition?, limit: Int): List<Account>

    fun findById(id: AccountId): Account?

    /**
     * 名前で引く。大文字小文字の違いは無視する
     */
    fun findByUsername(username: String): Account?

    /**
     * 名前でまとめて引く。大文字小文字の違いは無視する。
     *
     * 返すマップのキーは渡された名前、値は対応するアカウント
     */
    fun findByUsernames(usernames: Collection<String>): Map<String, Account>

    /**
     * 追加する。同じ名前が既にあれば null を返す。
     *
     * 名前が使える形式かどうかはここでは見ない。保存できる文字列かどうかと、
     * アクターの名前として使えるかどうかは別の話なので、呼び出し側で確かめる。
     */
    fun add(
        username: String,
        createdAt: Instant,
    ): Account?

    fun updateProfile(
        id: AccountId,
        displayName: String?,
        summary: String?,
    ): Account?

    /**
     * 消したアカウントを名前で引く。生きているものは返さない。
     *
     * 送り残した配信に署名するためだけの口。通常の引き当て（`ActorDirectory`）に
     * 出すと、消したアカウントが外から見えたままになる。
     */
    fun findDeletedByUsername(username: String): Account?

    /**
     * アカウントを消して、消したことを宛先ごとに投函する。
     *
     * 行は残して消した時刻を入れる。消えたアカウントとして署名する `Delete{Actor}` を
     * 送り切るまで、名前を押さえたままにする必要がある。
     *
     * 一緒に消えるのは、登録したフィードと取り込んだ記事（外部キー）、配信した投稿、
     * フォロワー、まだ送っていない配信。名前で持っているもの（投稿とフォロワー）を
     * 残すと、同じ名前で作り直したアカウントに引き継がれる。送り残した配信を残すと、
     * 消えたアカウントの投稿が後から届く。
     *
     * 全部を 1 トランザクションで確定させる。途中で切れると、消えたはずの
     * アカウントの投稿が引けたり、誰にも消えたことが伝わらないまま残ったりする。
     *
     * @return 既に消えているか行が無ければ null
     */
    fun markDeleted(deletion: AccountDeletion): AccountDeletionResult?

    /**
     * 消したアカウントのうち、送る配信が 1 件も残っていないものを本当に消す。
     *
     * 諦めた配信（`failed`）も残っている間は消さない。何を送れなかったのかが
     * 分からなくなる。消すまでその名前は空かない。
     *
     * @return 消えた件数
     */
    fun purgeDeleted(): Int
}

/**
 * 消すアカウントと、消したことを伝える配信。
 *
 * @param id 消すアカウント
 * @param username 署名するこちらのアカウントの名前
 * @param body 署名対象になる `Delete{Actor}` の JSON
 * @param inboxes 宛先。同じ宛先は 1 つにまとめてから渡すこと
 * @param deletedAt 消した時刻。投函した時刻としても記録する
 */
data class AccountDeletion(
    val id: AccountId,
    val username: String,
    val body: String,
    val inboxes: List<String>,
    val deletedAt: Instant,
)

/**
 * @param deletedNotes 消した投稿の数
 * @param removedFollowers 外したフォロワーの数。`Accept` を返せていないものも含む
 * @param deliveries 投函した配信の数。宛先の数と同じ
 */
data class AccountDeletionResult(
    val deletedNotes: Int,
    val removedFollowers: Int,
    val deliveries: Int,
)

/**
 * ページの位置。
 *
 * 並び順の鍵をそのまま持つ。名前で位置を指すと、その行が消えたときに続きを引けず、
 * 同じ名前で作り直されたときは新しい行の位置から返してしまう。
 */
data class AccountPosition(
    val createdAt: Instant,
    val id: AccountId,
)

/**
 * 応答するアカウント 1 つ。
 *
 * @param username `acct:<username>@<domain>` と `/users/<username>` に入る名前
 * @param deletedAt 消した時刻。生きているアカウントでは null
 */
data class Account(
    val id: AccountId,
    val username: String,
    val createdAt: Instant,
    val displayName: String?,
    val summary: String?,
    val deletedAt: Instant?,
) {
    fun position(): AccountPosition = AccountPosition(createdAt = createdAt, id = id)
}
