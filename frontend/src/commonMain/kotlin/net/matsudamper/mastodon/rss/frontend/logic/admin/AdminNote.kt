package net.matsudamper.mastodon.rss.frontend.logic.admin

/**
 * 配信した投稿 1 件。
 *
 * @param url 相手がパーマリンクとして開く URL
 * @param contentHtml 配信した本文の HTML。画面にはタグを外して出す
 * @param publishedAt エポックからの秒数
 */
data class AdminNote(
    val url: String,
    val contentHtml: String,
    val publishedAt: Long,
)

sealed interface AdminNotesResult {
    /**
     * @param cursor 次のページを取るときに渡す。null なら最後のページ
     */
    data class Success(
        val notes: List<AdminNote>,
        val cursor: String?,
    ) : AdminNotesResult

    data class Failure(
        val message: String,
    ) : AdminNotesResult
}

sealed interface AdminPostNoteResult {
    /**
     * 記録できた。配信の成否は別で、[delivered] が [deliveryTargets] より
     * 少なければ届かなかった相手がいる
     */
    data class Success(
        val note: AdminNote,
        val deliveryTargets: Int,
        val delivered: Int,
    ) : AdminPostNoteResult

    /**
     * 入力が通らなかった。当てはまる理由が入る
     */
    data class Rejected(
        val unknownAccount: Boolean,
        val isEmpty: Boolean,
        val maxLength: Int?,
    ) : AdminPostNoteResult

    data class Failure(
        val message: String,
    ) : AdminPostNoteResult
}
