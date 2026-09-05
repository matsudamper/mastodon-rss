package net.matsudamper.mastodon.rss.frontend.logic.admin

import net.matsudamper.mastodon.rss.frontend.logic.account.Account

/**
 * @param createdAt 追加した時刻。エポックからの秒数
 * @param displayName プロフィールの表示名。未設定なら null
 * @param summary プロフィールの説明文。未設定なら null
 */
data class AdminAccount(
    val account: Account,
    val createdAt: Long,
    val displayName: String?,
    val summary: String?,
    val followerCount: Int,
    val feed: AdminFeed?,
)
