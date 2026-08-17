package net.matsudamper.mastodon.rss.frontend.logic.account
sealed interface AccountResult {
    data class Success(
        val account: Account,
    ) : AccountResult

    data object NotFound : AccountResult

    data class Failure(
        val message: String,
    ) : AccountResult
}
