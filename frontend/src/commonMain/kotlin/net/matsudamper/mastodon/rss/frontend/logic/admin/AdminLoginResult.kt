package net.matsudamper.mastodon.rss.frontend.logic.admin
sealed interface AdminLoginResult {
    data object Success : AdminLoginResult

    data object WrongPassword : AdminLoginResult

    data object NotConfigured : AdminLoginResult

    data class Failure(
        val message: String,
    ) : AdminLoginResult
}
