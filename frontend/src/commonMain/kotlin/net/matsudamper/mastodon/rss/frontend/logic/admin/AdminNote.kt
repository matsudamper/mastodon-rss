package net.matsudamper.mastodon.rss.frontend.logic.admin

/**
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
     * 記録できた。[delivered] が [deliveryTargets] より少なければ届かなかった相手がいる
     */
    data class Success(
        val note: AdminNote,
        val deliveryTargets: Int,
        val delivered: Int,
    ) : AdminPostNoteResult

    /**
     * 入力が通らなかった
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
