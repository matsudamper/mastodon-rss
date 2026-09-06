package net.matsudamper.mastodon.rss.frontend.logic.account

/**
 * このアカウントをフォローしている相手。
 *
 * 分かるのはアクター文書の URL だけ。表示名やアイコンはこちらに無い
 */
data class AccountFollower(
    val actorUrl: String,
)

sealed interface AccountFollowersResult {
    /**
     * @param cursor 次のページを取るときに渡す。null なら最後のページ
     */
    data class Success(
        val followers: List<AccountFollower>,
        val cursor: String?,
    ) : AccountFollowersResult

    data class Failure(
        val message: String,
    ) : AccountFollowersResult
}
