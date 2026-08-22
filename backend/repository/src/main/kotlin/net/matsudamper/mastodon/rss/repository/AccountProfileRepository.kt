package net.matsudamper.mastodon.rss.repository

/**
 * アクターの表示名と説明文。
 *
 * 行が無いときは呼び出し側が既定値を使う。組み込みアカウントも username で引ける。
 */
interface AccountProfileRepository {
    /**
     * 名前で引く。大文字小文字の違いは無視する
     */
    fun findByUsername(username: String): AccountProfile?

    /**
     * 名前でまとめて引く。大文字小文字の違いは無視する。
     *
     * 返すマップのキーは渡された名前、値は対応するプロフィール
     */
    fun findByUsernames(usernames: Collection<String>): Map<String, AccountProfile>

    /**
     * 保存する。同じ名前があれば上書きする
     */
    fun upsert(
        username: String,
        displayName: String,
        summary: String,
    ): AccountProfile
}

/**
 * @param username [AccountProfileRepository] で引くときの名前
 */
data class AccountProfile(
    val username: String,
    val displayName: String,
    val summary: String,
)
