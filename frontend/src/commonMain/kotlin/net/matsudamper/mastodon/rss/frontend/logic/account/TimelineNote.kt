package net.matsudamper.mastodon.rss.frontend.logic.account

/**
 * タイムラインに並ぶ投稿 1 件。投稿したアカウントも一緒に持つ
 */
data class TimelineNote(
    val note: AccountNote,
    val account: HomeAccount,
)

sealed interface TimelineResult {
    /**
     * @param cursor 次のページを取るときに渡す。null なら最後のページ
     */
    data class Success(
        val notes: List<TimelineNote>,
        val cursor: String?,
    ) : TimelineResult

    data class Failure(
        val message: String,
    ) : TimelineResult
}
