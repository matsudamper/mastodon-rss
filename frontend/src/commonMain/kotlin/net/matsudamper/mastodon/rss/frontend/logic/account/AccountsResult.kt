package net.matsudamper.mastodon.rss.frontend.logic.account

sealed interface AccountsResult {
    data class Success(
        val accounts: List<HomeAccount>,
        val hasMore: Boolean,
        val nextCursor: String?,
    ) : AccountsResult

    data class Failure(
        val message: String,
    ) : AccountsResult
}

data class HomeAccount(
    val id: Long,
    val username: String,
    val acct: String,
)
