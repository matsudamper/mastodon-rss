package net.matsudamper.mastodon.rss.repository.sqlite

import net.matsudamper.mastodon.rss.repository.NoteStampRepository
import net.matsudamper.mastodon.rss.repository.NoteStampRepository.NewNoteStamp
import net.matsudamper.mastodon.rss.repository.NoteStampRepository.StampCount
import net.matsudamper.mastodon.rss.repository.jooq.Tables.NOTES
import net.matsudamper.mastodon.rss.repository.jooq.Tables.NOTE_STAMPS
import net.matsudamper.mastodon.rss.repository.jooq.Tables.REMOTE_ACTORS
import net.matsudamper.mastodon.rss.shared.PublicNoteId
import org.jooq.impl.DSL

internal class SqliteNoteStampRepository(
    private val jooq: SqliteJooq,
) : NoteStampRepository {
    override fun put(stamp: NewNoteStamp): Boolean = jooq.transaction { dsl ->
        // 配信していない投稿へのスタンプは外部キーで弾かれる。例外で落とすと
        // inbox の処理の失敗として記録されるだけなので、先に見て false で返す
        val noteExists = dsl.fetchExists(
            DSL
                .selectOne()
                .from(NOTES)
                .where(NOTES.PUBLIC_ID.eq(stamp.notePublicId.value)),
        )
        if (noteExists.not()) return@transaction false

        val remoteActorId = RemoteActorRows.upsert(dsl = dsl, actor = stamp.actor, now = stamp.receivedAt)
        val createdAt = StoredInstant.format(stamp.receivedAt)

        dsl
            .insertInto(NOTE_STAMPS)
            .set(NOTE_STAMPS.NOTE_PUBLIC_ID, stamp.notePublicId.value)
            .set(NOTE_STAMPS.REMOTE_ACTOR_ID, remoteActorId)
            .set(NOTE_STAMPS.EMOJI, stamp.emoji)
            .set(NOTE_STAMPS.EMOJI_IMAGE_URL, stamp.emojiImageUrl)
            .set(NOTE_STAMPS.CREATED_AT, createdAt)
            .onConflict(NOTE_STAMPS.NOTE_PUBLIC_ID, NOTE_STAMPS.REMOTE_ACTOR_ID)
            .doUpdate()
            .set(NOTE_STAMPS.EMOJI, stamp.emoji)
            .set(NOTE_STAMPS.EMOJI_IMAGE_URL, stamp.emojiImageUrl)
            .set(NOTE_STAMPS.CREATED_AT, createdAt)
            .execute()

        true
    }

    override fun remove(
        notePublicId: PublicNoteId,
        actorUri: String,
        emoji: String,
    ): Boolean = jooq.transaction { dsl ->
        dsl
            .deleteFrom(NOTE_STAMPS)
            .where(NOTE_STAMPS.NOTE_PUBLIC_ID.eq(notePublicId.value))
            .and(NOTE_STAMPS.REMOTE_ACTOR_ID.`in`(RemoteActorRows.id(actorUri)))
            .and(NOTE_STAMPS.EMOJI.eq(emoji))
            .execute() > 0
    }

    override fun removeByActor(actorUri: String): Int = jooq.transaction { dsl ->
        dsl
            .deleteFrom(NOTE_STAMPS)
            .where(NOTE_STAMPS.REMOTE_ACTOR_ID.`in`(RemoteActorRows.id(actorUri)))
            .execute()
    }

    override fun findPublicKeyPem(actorUri: String): String? = jooq.withConnection { dsl ->
        dsl
            .select(REMOTE_ACTORS.PUBLIC_KEY_PEM)
            .from(REMOTE_ACTORS)
            .join(NOTE_STAMPS)
            .on(NOTE_STAMPS.REMOTE_ACTOR_ID.eq(REMOTE_ACTORS.ID))
            .where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri))
            .limit(1)
            .fetchOne(REMOTE_ACTORS.PUBLIC_KEY_PEM)
    }

    override fun countsByNotes(notePublicIds: Set<PublicNoteId>): Map<PublicNoteId, List<StampCount>> {
        if (notePublicIds.isEmpty()) return mapOf()

        return jooq.withConnection { dsl ->
            val count = DSL.count()

            dsl
                .select(
                    NOTE_STAMPS.NOTE_PUBLIC_ID,
                    NOTE_STAMPS.EMOJI,
                    DSL.max(NOTE_STAMPS.EMOJI_IMAGE_URL),
                    count,
                )
                .from(NOTE_STAMPS)
                .where(NOTE_STAMPS.NOTE_PUBLIC_ID.`in`(notePublicIds.map { it.value }))
                .groupBy(NOTE_STAMPS.NOTE_PUBLIC_ID, NOTE_STAMPS.EMOJI)
                // 同じ数なら綴りで並べる。決めておかないと、読み直すたびに並びが入れ替わる
                .orderBy(count.desc(), NOTE_STAMPS.EMOJI.asc())
                .fetch()
                .groupBy(
                    { PublicNoteId(it.value1()) },
                    {
                        StampCount(
                            emoji = it.value2(),
                            emojiImageUrl = it.value3(),
                            count = it.value4(),
                        )
                    },
                )
        }
    }
}
