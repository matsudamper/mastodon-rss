package net.matsudamper.mastodon.rss.frontend.ui

data class NoteReactionsUiState(
    val favouriteCount: String?,
    val boostCount: String?,
    val stamps: List<Stamp>,
) {
    data class Stamp(
        val name: String,
        val imageUrl: String?,
        val count: String,
    )
}
