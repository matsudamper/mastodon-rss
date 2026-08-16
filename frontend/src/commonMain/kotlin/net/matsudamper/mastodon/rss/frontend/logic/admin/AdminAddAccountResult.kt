package net.matsudamper.mastodon.rss.frontend.logic.admin
sealed interface AdminAddAccountResult {
    data class Success(
        val account: AdminAccount,
    ) : AdminAddAccountResult

    data object InvalidUsername : AdminAddAccountResult

    data object Duplicated : AdminAddAccountResult

    data class Failure(
        val message: String,
    ) : AdminAddAccountResult
}
