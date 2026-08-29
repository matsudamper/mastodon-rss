package net.matsudamper.mastodon.rss.frontend.logic.admin

import kotlin.time.Instant

/**
 * @param feedItem この投稿の元になった取り込み済みの記事。手で書いた投稿と、
 *   記事を消した後は null
 */
data class AdminNote(
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
