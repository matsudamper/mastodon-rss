package net.matsudamper.mastodon.rss.frontend.screen

import net.matsudamper.mastodon.rss.frontend.logic.account.NoteStamp
import net.matsudamper.mastodon.rss.frontend.ui.NoteReactionsUiState

/**
 * ホーム・アカウント・投稿 1 件の画面が、お気に入りとスタンプを同じ形で出すために通す
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
