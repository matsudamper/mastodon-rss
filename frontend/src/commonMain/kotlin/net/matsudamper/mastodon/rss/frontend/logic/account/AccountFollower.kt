package net.matsudamper.mastodon.rss.frontend.logic.account

/**
 * このアカウントをフォローしている相手。
 *
 * @param acct Mastodon の検索窓に貼る形。取れていなければ null
 */
data class AccountFollower(
    val actorUrl: String,
    val acct: String?,
)

sealed interface AccountFollowersResult {
    data class Success(
        val followers: List<AccountFollower>,
        val nextCursor: String?,
    ) : AccountFollowersResult

    /**
     * この名前のアカウントは無い
     */
    data object NotFound : AccountFollowersResult

    data class Failure(
        val message: String,
    ) : AccountFollowersResult
}
