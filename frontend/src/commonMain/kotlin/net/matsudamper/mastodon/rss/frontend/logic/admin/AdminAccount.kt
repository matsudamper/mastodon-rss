package net.matsudamper.mastodon.rss.frontend.logic.admin

import net.matsudamper.mastodon.rss.frontend.logic.account.Account

/**
 * @param createdAt 追加した時刻。エポックからの秒数
 */
data class AdminAccount(
    val account: Account,
    val createdAt: Long,
    val followerCount: Int,
    val feed: AdminFeed?,
)
