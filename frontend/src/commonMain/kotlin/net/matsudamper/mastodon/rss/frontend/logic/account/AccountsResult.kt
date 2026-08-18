package net.matsudamper.mastodon.rss.frontend.logic.account

sealed interface AccountsResult {
    data class Success(
        val accounts: List<Account>,
        val hasMore: Boolean,
        val nextCursor: String?,
    ) : AccountsResult

    data class Failure(
        val message: String,
    ) : AccountsResult
}
