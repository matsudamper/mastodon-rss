package net.matsudamper.mastodon.rss.frontend.logic.account

import kotlin.time.Instant

data class AccountNote(
    val id: String,
    val url: String,
    val contentHtml: String,
    val publishedAt: Instant,
    val favouriteCount: Int,
    val reactions: List<NoteReaction>,
)

/**
 * 投稿に届いたスタンプ 1 種類。
 *
 * @param name 絵文字そのもの、またはカスタム絵文字の名前
 * @param imageUrl カスタム絵文字の画像。絵文字そのものなら null
 */
data class NoteReaction(
    val name: String,
    val imageUrl: String?,
    val count: Int,
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

sealed interface AccountNoteResult {
    data class Success(
        val note: AccountNote,
    ) : AccountNoteResult

    data object NotFound : AccountNoteResult

    data class Failure(
        val message: String,
    ) : AccountNoteResult
}
