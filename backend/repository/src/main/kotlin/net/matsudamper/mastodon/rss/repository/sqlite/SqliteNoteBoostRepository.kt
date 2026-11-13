package net.matsudamper.mastodon.rss.repository.sqlite

import net.matsudamper.mastodon.rss.repository.NoteBoostRepository
import net.matsudamper.mastodon.rss.repository.NoteBoostRepository.NewNoteBoost
import net.matsudamper.mastodon.rss.repository.jooq.Tables.NOTES
import net.matsudamper.mastodon.rss.repository.jooq.Tables.NOTE_BOOSTS
import net.matsudamper.mastodon.rss.repository.jooq.Tables.REMOTE_ACTORS
import net.matsudamper.mastodon.rss.shared.PublicNoteId
import org.jooq.impl.DSL

internal class SqliteNoteBoostRepository(
    private val jooq: SqliteJooq,
) : NoteBoostRepository {
    override fun add(boost: NewNoteBoost): Boolean = jooq.transaction { dsl ->
        // 外部キーで落とすと inbox の処理の失敗として記録されるだけなので、先に見て false で返す
        val noteExists = dsl.fetchExists(
            DSL
                .selectOne()
                .from(NOTES)
                .where(NOTES.PUBLIC_ID.eq(boost.notePublicId.value)),
        )
        if (noteExists.not()) return@transaction false

        val remoteActorId = RemoteActorRows.upsert(dsl = dsl, actor = boost.actor, now = boost.receivedAt)

        val inserted = dsl
            .insertInto(NOTE_BOOSTS)
            .set(NOTE_BOOSTS.NOTE_PUBLIC_ID, boost.notePublicId.value)
            .set(NOTE_BOOSTS.REMOTE_ACTOR_ID, remoteActorId)
            .set(NOTE_BOOSTS.ACTIVITY_URI, boost.activityUri)
            .set(NOTE_BOOSTS.CREATED_AT, StoredInstant.format(boost.receivedAt))
            .onConflictDoNothing()
            .execute()

        inserted > 0
    }

    override fun removeByActivity(
        actorUri: String,
        activityUri: String,
    ): Boolean = jooq.transaction { dsl ->
        dsl
            .deleteFrom(NOTE_BOOSTS)
            .where(NOTE_BOOSTS.ACTIVITY_URI.eq(activityUri))
            .and(NOTE_BOOSTS.REMOTE_ACTOR_ID.`in`(RemoteActorRows.id(actorUri)))
            .execute() > 0
    }

    override fun removeByActor(actorUri: String): Int = jooq.transaction { dsl ->
        dsl
            .deleteFrom(NOTE_BOOSTS)
            .where(NOTE_BOOSTS.REMOTE_ACTOR_ID.`in`(RemoteActorRows.id(actorUri)))
            .execute()
    }

    override fun findPublicKeyPem(actorUri: String): String? = jooq.withConnection { dsl ->
        dsl
            .select(REMOTE_ACTORS.PUBLIC_KEY_PEM)
            .from(REMOTE_ACTORS)
            .join(NOTE_BOOSTS)
            .on(NOTE_BOOSTS.REMOTE_ACTOR_ID.eq(REMOTE_ACTORS.ID))
            .where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri))
            .limit(1)
            .fetchOne(REMOTE_ACTORS.PUBLIC_KEY_PEM)
    }

    override fun countsByNotes(notePublicIds: Set<PublicNoteId>): Map<PublicNoteId, Int> {
        if (notePublicIds.isEmpty()) return mapOf()

        return jooq.withConnection { dsl ->
            dsl
                .select(NOTE_BOOSTS.NOTE_PUBLIC_ID, DSL.count())
                .from(NOTE_BOOSTS)
                .where(NOTE_BOOSTS.NOTE_PUBLIC_ID.`in`(notePublicIds.map { it.value }))
                .groupBy(NOTE_BOOSTS.NOTE_PUBLIC_ID)
                .fetch()
                .associate { PublicNoteId(it.value1()) to it.value2() }
        }
    }
}
