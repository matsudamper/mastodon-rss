package net.matsudamper.mastodon.rss.frontend.logic.account

sealed interface AccountResult {
    /**
     * @param iconUrl プロフィール画像の URL。無ければ null
     * @param headerUrl プロフィールヘッダー画像の URL。無ければ null
     */
    data class Success(
        val account: Account,
        val iconUrl: String?,
        val headerUrl: String?,
        val followerCount: Int,
        val noteCount: Int,
        val feed: AccountFeed?,
    ) : AccountResult

    data object NotFound : AccountResult

    data class Failure(
        val message: String,
    ) : AccountResult
}
