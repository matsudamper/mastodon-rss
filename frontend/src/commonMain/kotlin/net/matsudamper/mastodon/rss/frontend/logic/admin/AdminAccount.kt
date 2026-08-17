package net.matsudamper.mastodon.rss.frontend.logic.admin
/**
 * @param createdAt 追加した時刻。エポックからの秒数。設定で決まるアカウントには無い
 */
data class AdminAccount(
    val username: String,
    val acct: String,
    val actorUrl: String,
    val createdAt: Long?,
    val followerCount: Int,
)
