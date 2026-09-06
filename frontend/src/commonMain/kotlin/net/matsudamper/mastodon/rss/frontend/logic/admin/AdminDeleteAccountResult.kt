package net.matsudamper.mastodon.rss.frontend.logic.admin

sealed interface AdminDeleteAccountResult {
    data object Success : AdminDeleteAccountResult

    data class Rejected(
        val reason: FailureReason,
    ) : AdminDeleteAccountResult

    data class Failure(
        val message: String,
    ) : AdminDeleteAccountResult

    enum class FailureReason {
        UNKNOWN_ACCOUNT,
        UNKNOWN,
    }
}
