package net.matsudamper.mastodon.rss.frontend.logic.account

/**
 * @param username 要求した綴りではなく、サーバーが持っている綴り
 * @param acct Mastodon の検索窓に貼る形
 * @param actorUrl ActivityPub の Actor の URL
 * @param displayName プロフィールの表示名。未設定なら空文字
 * @param summary プロフィールの説明文。未設定なら空文字
 */
data class Account(
    val id: Long,
    val username: String,
    val acct: String,
    val actorUrl: String,
    val displayName: String,
    val summary: String,
)

data class AccountFeed(
    val feedUrl: String,
    val siteUrl: String?,
)
