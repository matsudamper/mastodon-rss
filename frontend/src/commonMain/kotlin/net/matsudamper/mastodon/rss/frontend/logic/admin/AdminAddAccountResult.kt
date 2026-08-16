package net.matsudamper.mastodon.rss.frontend.logic.admin
sealed interface AdminAddAccountResult {
    data class Success(
        val acct: String,
    ) : AdminAddAccountResult

    /**
     * @param characters 入力に含まれていた使えない文字
     */
    data class UnusableCharacter(
        val characters: List<String>,
    ) : AdminAddAccountResult

    data class TooLong(
        val maxLength: Int,
    ) : AdminAddAccountResult

    data object Empty : AdminAddAccountResult

    data object Duplicated : AdminAddAccountResult

    data class Failure(
        val message: String,
    ) : AdminAddAccountResult
}
