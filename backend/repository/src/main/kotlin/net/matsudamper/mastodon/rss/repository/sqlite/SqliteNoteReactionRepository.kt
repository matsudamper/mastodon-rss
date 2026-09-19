package net.matsudamper.mastodon.rss.repository.sqlite

import net.matsudamper.mastodon.rss.repository.NewNoteReaction
import net.matsudamper.mastodon.rss.repository.NoteReactionCount
import net.matsudamper.mastodon.rss.repository.NoteReactionRepository
import net.matsudamper.mastodon.rss.repository.jooq.Tables.NOTES
import net.matsudamper.mastodon.rss.repository.jooq.Tables.NOTE_REACTIONS
import net.matsudamper.mastodon.rss.shared.PublicNoteId
import org.jooq.impl.DSL

internal class SqliteNoteReactionRepository(
    private val jooq: SqliteJooq,
) : NoteReactionRepository {
    override fun add(reaction: NewNoteReaction): Boolean = jooq.transaction { dsl ->
        // 配信していない投稿への反応は外部キーで弾かれる。例外で落とすと
        // inbox の処理の失敗として記録されるだけなので、先に見て false で返す
        val noteExists = dsl.fetchExists(
            DSL
                .selectOne()
                .from(NOTES)
                .where(NOTES.PUBLIC_ID.eq(reaction.notePublicId.value)),
        )
        if (noteExists.not()) return@transaction false

        val inserted = dsl
            .insertInto(NOTE_REACTIONS)
            .set(NOTE_REACTIONS.NOTE_PUBLIC_ID, reaction.notePublicId.value)
            .set(NOTE_REACTIONS.ACTOR_URI, reaction.actorUri)
            .set(NOTE_REACTIONS.ACTIVITY_URI, reaction.activityUri)
            .set(NOTE_REACTIONS.EMOJI, reaction.emoji)
            .set(NOTE_REACTIONS.EMOJI_IMAGE_URL, reaction.emojiImageUrl)
            .set(NOTE_REACTIONS.CREATED_AT, StoredInstant.format(reaction.receivedAt))
            .onConflictDoNothing()
            .execute()

        inserted > 0
    }

    override fun removeByActivityUri(
        actorUri: String,
        activityUri: String,
    ): Boolean = jooq.transaction { dsl ->
        dsl
            .deleteFrom(NOTE_REACTIONS)
            .where(NOTE_REACTIONS.ACTIVITY_URI.eq(activityUri))
            .and(NOTE_REACTIONS.ACTOR_URI.eq(actorUri))
            .execute() > 0
    }

    override fun removeByEmoji(
        notePublicId: PublicNoteId,
        actorUri: String,
        emoji: String,
    ): Boolean = jooq.transaction { dsl ->
        dsl
            .deleteFrom(NOTE_REACTIONS)
            .where(NOTE_REACTIONS.NOTE_PUBLIC_ID.eq(notePublicId.value))
            .and(NOTE_REACTIONS.ACTOR_URI.eq(actorUri))
            .and(NOTE_REACTIONS.EMOJI.eq(emoji))
            .execute() > 0
    }

    override fun removeByActor(actorUri: String): Int = jooq.transaction { dsl ->
        dsl
            .deleteFrom(NOTE_REACTIONS)
            .where(NOTE_REACTIONS.ACTOR_URI.eq(actorUri))
            .execute()
    }

    override fun countsByNotes(notePublicIds: Set<PublicNoteId>): Map<PublicNoteId, List<NoteReactionCount>> {
        if (notePublicIds.isEmpty()) return mapOf()

        return jooq.withConnection { dsl ->
            val count = DSL.count()

            dsl
                .select(
                    NOTE_REACTIONS.NOTE_PUBLIC_ID,
                    NOTE_REACTIONS.EMOJI,
                    DSL.max(NOTE_REACTIONS.EMOJI_IMAGE_URL),
                    count,
                )
                .from(NOTE_REACTIONS)
                .where(NOTE_REACTIONS.NOTE_PUBLIC_ID.`in`(notePublicIds.map { it.value }))
                .groupBy(NOTE_REACTIONS.NOTE_PUBLIC_ID, NOTE_REACTIONS.EMOJI)
                // 同じ数なら綴りで並ぶ。決めておかないと、読み直すたびに並びが入れ替わる
                .orderBy(count.desc(), NOTE_REACTIONS.EMOJI.asc())
                .fetch()
                .groupBy(
                    { PublicNoteId(it.value1()) },
                    {
                        NoteReactionCount(
                            emoji = it.value2(),
                            emojiImageUrl = it.value3(),
                            count = it.value4(),
                        )
                    },
                )
        }
    }
}
