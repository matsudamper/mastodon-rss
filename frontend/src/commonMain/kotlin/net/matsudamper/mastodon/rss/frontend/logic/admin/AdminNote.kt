package net.matsudamper.mastodon.rss.frontend.logic.admin

import kotlin.time.Instant

/**
 * @param id 投稿を指す id。削除に使う
 * @param feedItem この投稿の元になった取り込み済みの記事。手で書いた投稿と、
 *   記事を消した後は null
 */
data class AdminNote(
    val id: String,
    val url: String,
    val contentHtml: String,
    val publishedAt: Instant,
    val feedItem: AdminFeedItem?,
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

sealed interface AdminDeleteNoteResult {
    /**
     * @param deliveryTargets Delete を送った宛先の数
     * @param delivered そのうち届いた数。少なければ、その相手には投稿が残っている
     */
    data class Success(
        val deliveryTargets: Int,
        val delivered: Int,
    ) : AdminDeleteNoteResult

    data class Rejected(
        val reason: FailureReason,
    ) : AdminDeleteNoteResult

    data class Failure(
        val message: String,
    ) : AdminDeleteNoteResult

    enum class FailureReason {
        UNKNOWN_ACCOUNT,
        NOT_FOUND,
        UNKNOWN,
    }
}
