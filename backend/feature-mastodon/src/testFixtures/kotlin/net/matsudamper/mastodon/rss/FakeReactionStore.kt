package net.matsudamper.mastodon.rss

import net.matsudamper.mastodon.rss.entity.PublicNoteId
import net.matsudamper.mastodon.rss.reaction.ReactionStore
import net.matsudamper.mastodon.rss.reaction.ReceivedReaction

/**
 * 反応の記録の差し替え。オンメモリで持つ。
 *
 * SQL の振る舞いは `:backend:repository` のテストが本物の SQLite で確かめる。
 * こちらが受け持つのは、inbox のハンドラが何を記録して何を消したかの確認。
 */
class FakeReactionStore : ReactionStore {
    val rows: MutableList<ReceivedReaction> = mutableListOf()

    override fun add(reaction: ReceivedReaction): Boolean {
        // 一意制約と同じ判定
        val duplicated = rows.any {
            it.activityUri == reaction.activityUri ||
                (
                    it.notePublicId == reaction.notePublicId &&
                        it.actorUri == reaction.actorUri &&
                        it.emoji == reaction.emoji
                    )
        }
        if (duplicated) return false

        rows += reaction
        return true
    }

    override fun removeByActivityUri(
        actorUri: String,
        activityUri: String,
    ): Boolean = rows.removeAll { it.actorUri == actorUri && it.activityUri == activityUri }

    override fun removeByEmoji(
        notePublicId: PublicNoteId,
        actorUri: String,
        emoji: String,
    ): Boolean = rows.removeAll {
        it.notePublicId == notePublicId && it.actorUri == actorUri && it.emoji == emoji
    }

    override fun removeActor(actorUri: String): Int {
        val before = rows.size
        rows.removeAll { it.actorUri == actorUri }
        return before - rows.size
    }
}
