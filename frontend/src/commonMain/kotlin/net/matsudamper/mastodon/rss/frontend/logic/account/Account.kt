package net.matsudamper.mastodon.rss.frontend.logic.account
/**
 * @param username 要求した綴りではなく、サーバーが持っている綴り
 * @param acct Mastodon の検索窓に貼る形
 * @param actorUrl ActivityPub の Actor の URL
 */
data class Account(
    val id: Long,
    val username: String,
    val acct: String,
    val actorUrl: String,
)
