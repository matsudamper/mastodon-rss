package net.matsudamper.mastodon.rss.frontend.logic.account
sealed interface AccountResult {
    data class Success(
        val account: Account,
    ) : AccountResult

    /**
     * その名前のアカウントが無い。引けなかった [Failure] と混ぜない
     */
    data object NotFound : AccountResult

    data class Failure(
        val message: String,
    ) : AccountResult
}
