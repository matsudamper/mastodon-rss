package net.matsudamper.mastodon.rss.frontend.logic.account

import kotlin.time.Instant

data class AccountNote(
    val id: String,
    val url: String,
    val contentHtml: String,
    val publishedAt: Instant,
    val favouriteCount: Int,
    val stamps: List<NoteStamp>,
)

data class NoteStamp(
    val name: String,
    val imageUrl: String?,
    val count: Int,
)

/**
 * アカウントの投稿一覧に並ぶ 1 件
 *
 * @param linkUrls 本文にあるリンク。OGP は [NoteLinkPreview] として別に取る
 */
data class AccountListedNote(
    val note: AccountNote,
    val linkUrls: List<String>,
)

sealed interface AccountNotesResult {
    /**
     * @param cursor 次のページを取るときに渡す。null なら最後のページ
     */
    data class Success(
        val notes: List<AccountListedNote>,
        val cursor: String?,
    ) : AccountNotesResult

    data class Failure(
        val message: String,
    ) : AccountNotesResult
}

sealed interface AccountNoteResult {
    /**
     * @param linkUrls 本文にあるリンク。OGP は [NoteLinkPreview] として別に取る
     */
    data class Success(
        val note: AccountNote,
        val account: HomeAccount,
        val linkUrls: List<String>,
    ) : AccountNoteResult

    data object NotFound : AccountNoteResult

    data class Failure(
        val message: String,
    ) : AccountNoteResult
}
