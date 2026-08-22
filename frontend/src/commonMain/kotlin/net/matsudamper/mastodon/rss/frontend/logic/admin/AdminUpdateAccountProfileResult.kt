package net.matsudamper.mastodon.rss.frontend.logic.admin

sealed interface AdminUpdateAccountProfileResult {
    data class Success(
        val adminAccount: AdminAccount,
    ) : AdminUpdateAccountProfileResult

    data class Rejected(
        val unknownAccount: Boolean,
        val emptyDisplayName: Boolean,
        val displayNameMaxLength: Int?,
        val summaryMaxLength: Int?,
    ) : AdminUpdateAccountProfileResult

    data class Failure(
        val message: String,
    ) : AdminUpdateAccountProfileResult
}
