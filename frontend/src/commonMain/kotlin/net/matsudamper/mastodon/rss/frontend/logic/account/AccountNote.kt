package net.matsudamper.mastodon.rss.frontend.logic.account

import kotlin.time.Instant

data class AccountNote(
    val url: String,
    val contentHtml: String,
    val publishedAt: Instant,
)

sealed interface AccountNotesResult {
    /**
     * @param cursor 次のページを取るときに渡す。null なら最後のページ
     */
    data class Success(
        val notes: List<AccountNote>,
        val cursor: String?,
    ) : AccountNotesResult

    data class Failure(
        val message: String,
    ) : AccountNotesResult
}
