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
     * 追加した順に返す
     */
    fun list(): List<Account>

    /**
     * 名前で引く。大文字小文字の違いは無視する
     */
    fun findByUsername(username: String): Account?

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

/**
 * 応答するアカウント 1 つ。
 *
 * @param username `acct:<username>@<domain>` と `/users/<username>` に入る名前
 */
data class Account(
    val username: String,
    val createdAt: Instant,
)
