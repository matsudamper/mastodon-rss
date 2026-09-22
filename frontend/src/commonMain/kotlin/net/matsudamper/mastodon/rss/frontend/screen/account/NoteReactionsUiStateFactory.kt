package net.matsudamper.mastodon.rss.frontend.screen.account

import net.matsudamper.mastodon.rss.frontend.logic.account.NoteReaction

/**
 * 投稿の一覧と投稿 1 件の画面が、届いた反応を同じ形で出すために通す
 */
internal object NoteReactionsUiStateFactory {
    fun create(
        favouriteCount: Int,
        reactions: List<NoteReaction>,
    ): NoteReactionsUiState? {
        if (favouriteCount <= 0 && reactions.isEmpty()) return null

        return NoteReactionsUiState(
            favouriteCount = favouriteCount.takeIf { it > 0 }?.toString(),
            stamps = reactions.map { reaction ->
                NoteReactionsUiState.Stamp(
                    name = reaction.name,
                    imageUrl = reaction.imageUrl,
                    count = reaction.count.toString(),
                )
            },
        )
    }
}
