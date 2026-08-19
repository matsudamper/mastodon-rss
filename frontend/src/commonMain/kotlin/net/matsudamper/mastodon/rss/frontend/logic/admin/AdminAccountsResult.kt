package net.matsudamper.mastodon.rss.frontend.logic.admin

sealed interface AdminAccountResult {
    /**
     * @param account 応答しない名前なら null
     */
    data class Success(
        val account: AdminAccount?,
    ) : AdminAccountResult

    data class Failure(
        val message: String,
    ) : AdminAccountResult
}

sealed interface AdminAccountsResult {
    data class Success(
        val accounts: List<AdminAccount>,
    ) : AdminAccountsResult

    data class Failure(
        val message: String,
    ) : AdminAccountsResult
}
