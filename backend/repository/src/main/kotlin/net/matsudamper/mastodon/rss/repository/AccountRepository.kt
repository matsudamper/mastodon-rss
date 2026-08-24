package net.matsudamper.mastodon.rss.repository

import java.time.Instant

/**
 * 応答するアカウントの読み書き。
 *
 * 名前は大文字小文字を区別せずに一意にする。区別して持てると同じ acct を指す行が
 * 2 つ並び、どちらを返すかが引き方で変わる。
 */
interface AccountRepository {
    /**
     * 追加した順に全件返す。
     *
     * 呼び出しを `list(afterUsername, limit)` に移し切ったら消す。
     * アカウントが増えるほど 1 回の応答が重くなり、上限も置けない
     */
    @Deprecated("ページングに移行する。list(afterUsername, limit) を使う")
    fun list(): List<Account>

    /**
     * 追加した順で `afterUsername` の次から `limit` 件返す。
     *
     * @param afterUsername null なら先頭から。その名前が無ければ空を返す
     */
    fun list(afterUsername: String?, limit: Int): List<Account>

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
}

@JvmInline
value class AccountId(
    val value: Long,
)

/**
 * 応答するアカウント 1 つ。
 *
 * @param username `acct:<username>@<domain>` と `/users/<username>` に入る名前
 */
data class Account(
    val id: AccountId,
    val username: String,
    val createdAt: Instant,
)
