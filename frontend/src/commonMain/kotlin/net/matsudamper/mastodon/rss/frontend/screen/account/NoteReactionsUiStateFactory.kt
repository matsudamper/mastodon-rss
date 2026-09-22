package net.matsudamper.mastodon.rss.frontend.screen.account

import net.matsudamper.mastodon.rss.frontend.logic.account.NoteReaction

/**
 * 投稿に届いた反応を画面に出す形にする。投稿の一覧と投稿 1 件の画面が同じ形で出す。
 */
internal object NoteReactionsUiStateFactory {
    /**
     * 1 つも届いていなければ null にして、反応の枠ごと出さない
     */
    fun create(
        favouriteCount: Int,
        reactions: List<NoteReaction>,
    ): NoteReactionsUiState? {
        if (favouriteCount <= 0 && reactions.isEmpty()) return null

        return NoteReactionsUiState(
            favouriteCount = favouriteCount.takeIf { it > 0 }?.toString(),
            stamps = reactions.map { reaction ->
                NoteReactionUiState(
                    name = reaction.name,
                    imageUrl = reaction.imageUrl,
                    count = reaction.count.toString(),
                )
            },
        )
    }
}
