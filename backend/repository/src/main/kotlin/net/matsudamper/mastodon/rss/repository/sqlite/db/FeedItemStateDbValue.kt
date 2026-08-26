package net.matsudamper.mastodon.rss.repository.sqlite.db

import net.matsudamper.mastodon.rss.repository.FeedItemState

internal enum class FeedItemStateDbValue(
    internal val dbValue: String,
) {
    PENDING("pending"),
    POSTED("posted"),
    SKIPPED("skipped"),
    ;

    fun toFeedItemState(): FeedItemState =
        when (this) {
            PENDING -> FeedItemState.PENDING
            POSTED -> FeedItemState.POSTED
            SKIPPED -> FeedItemState.SKIPPED
        }

    companion object {
        fun of(state: FeedItemState): FeedItemStateDbValue =
            when (state) {
                FeedItemState.PENDING -> PENDING
                FeedItemState.POSTED -> POSTED
                FeedItemState.SKIPPED -> SKIPPED
            }

        fun parse(value: String): FeedItemStateDbValue =
            entries.find { it.dbValue == value }
                ?: error("未知の記事状態: $value")
    }
}
