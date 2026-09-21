package net.matsudamper.mastodon.rss.frontend.logic.account

/**
 * @param url 相手のプロフィール。開くと相手のサーバーに飛ぶ
 * @param acct Mastodon の検索窓に貼る形。相手が名乗っていなければ null
 * @param displayName 表示名。名乗っていなければ null
 * @param iconUrl アイコン。無ければ null
 */
data class AccountFollower(
    val url: String,
    val acct: String?,
    val displayName: String?,
    val iconUrl: String?,
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
