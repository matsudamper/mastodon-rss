package net.matsudamper.mastodon.rss.frontend.screen

import net.matsudamper.mastodon.rss.frontend.logic.account.NoteStamp
import net.matsudamper.mastodon.rss.frontend.ui.NoteReactionsUiState

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
