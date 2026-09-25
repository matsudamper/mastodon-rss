package net.matsudamper.mastodon.rss.frontend.screen.account

import net.matsudamper.mastodon.rss.frontend.logic.account.NoteStamp

/**
 * @param favouriteCount 1 件も無ければ null
 */
data class NoteReactionsUiState(
    val favouriteCount: String?,
    val stamps: List<Stamp>,
) {
    /**
     * @param imageUrl カスタム絵文字の画像 URL。絵文字そのものなら null
     */
    data class Stamp(
        val name: String,
        val imageUrl: String?,
        val count: String,
    )
}

/**
 * 投稿の一覧と投稿 1 件の画面が、お気に入りとスタンプを同じ形で出すために通す
 */
internal object NoteReactionsUiStateFactory {
    /**
     * @return お気に入りもスタンプも無ければ null
     */
    fun create(
        favouriteCount: Int,
        stamps: List<NoteStamp>,
    ): NoteReactionsUiState? {
        if (favouriteCount <= 0 && stamps.isEmpty()) return null

        return NoteReactionsUiState(
            favouriteCount = favouriteCount.takeIf { it > 0 }?.toString(),
            stamps = stamps.map { stamp ->
                NoteReactionsUiState.Stamp(
                    name = stamp.name,
                    imageUrl = stamp.imageUrl,
                    count = stamp.count.toString(),
                )
            },
        )
    }
}
