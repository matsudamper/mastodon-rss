package net.matsudamper.mastodon.rss.frontend.logic.admin

import net.matsudamper.mastodon.rss.frontend.logic.account.Account

/**
 * @param createdAt 追加した時刻。エポックからの秒数。設定で決まるアカウントには無い
 */
data class AdminAccount(
    val account: Account,
    val deletable: Boolean,
    val createdAt: Long?,
    val followerCount: Int,
)
