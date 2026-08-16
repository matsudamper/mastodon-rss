package net.matsudamper.mastodon.rss.frontend.logic.admin
sealed interface AdminAccountsResult {
    data class Success(
        val accounts: List<AdminAccount>,
    ) : AdminAccountsResult

    data class Failure(
        val message: String,
    ) : AdminAccountsResult
}
