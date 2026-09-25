package net.matsudamper.mastodon.rss.frontend.ui

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
