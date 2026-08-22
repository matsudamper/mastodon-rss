package net.[REDACTED].mastodon.rss.frontend.logic.account

sealed interface AccountResult {
    data class Success(
        val account: Account,
        val notes: List<AccountNote>,
        /**
         * 次のページを取るときに渡す。null なら最後のページ
         */
        val notesCursor: String?,
    ) : AccountResult

    data object NotFound : AccountResult

    data class Failure(
        val message: String,
    ) : AccountResult
}
