package net.matsudamper.mastodon.rss.frontend.screen.account

import net.matsudamper.mastodon.rss.frontend.logic.account.NoteStamp

/**
 * @param imageUrl カスタム絵文字の画像 URL。絵文字そのものなら null
 */
data class NoteStampUiState(
    val name: String,
    val imageUrl: String?,
    val count: String,
)

/**
 * 投稿の一覧と投稿 1 件の画面が、スタンプを同じ形で出すために通す
 */
internal object NoteStampUiStateFactory {
    fun create(stamps: List<NoteStamp>): List<NoteStampUiState> = stamps.map { stamp ->
        NoteStampUiState(
            name = stamp.name,
            imageUrl = stamp.imageUrl,
            count = stamp.count.toString(),
        )
    }
}
