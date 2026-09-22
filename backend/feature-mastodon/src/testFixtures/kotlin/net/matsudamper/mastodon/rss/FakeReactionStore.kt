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
        // 一意制約と同じ判定。アクティビティの id は相手ごとに見る
        val duplicated = rows.any {
            it.actor.actorId == reaction.actor.actorId &&
                (
                    it.activityUri == reaction.activityUri ||
                        (it.notePublicId == reaction.notePublicId && it.emoji == reaction.emoji)
                    )
        }
        if (duplicated) return false

        // 本物は投稿ごとと相手ごとの両方で数を制限している
        val storedInNote = rows.count { it.notePublicId == reaction.notePublicId }
        if (storedInNote >= MAX_REACTIONS_PER_NOTE) return false

        val storedByActor = rows.count {
            it.notePublicId == reaction.notePublicId && it.actor.actorId == reaction.actor.actorId
        }
        if (storedByActor >= MAX_REACTIONS_PER_ACTOR) return false

        rows += reaction
        return true
    }

    override fun removeByActivityUri(
        actorUri: String,
        activityUri: String,
    ): Boolean = rows.removeAll { it.actor.actorId == actorUri && it.activityUri == activityUri }

    override fun removeByEmoji(
        notePublicId: PublicNoteId,
        actorUri: String,
        emoji: String,
    ): Boolean = rows.removeAll {
        it.notePublicId == notePublicId && it.actor.actorId == actorUri && it.emoji == emoji
    }

    override fun removeActor(actorUri: String): Int {
        val before = rows.size
        rows.removeAll { it.actor.actorId == actorUri }
        return before - rows.size
    }

    override fun findPublicKeyPem(actorUri: String): String? =
        rows.firstOrNull { it.actor.actorId == actorUri }?.actor?.publicKeyPem

    private companion object {
        /**
         * 1 つの投稿に、1 人の相手が持てる反応の数。本物と同じ数にしてある
         */
        const val MAX_REACTIONS_PER_ACTOR = 8

        /**
         * 1 つの投稿が持てる反応の数。本物と同じ数にしてある
         */
        const val MAX_REACTIONS_PER_NOTE = 500
    }
}
