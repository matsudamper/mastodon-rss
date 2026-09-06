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
