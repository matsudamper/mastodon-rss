package net.matsudamper.mastodon.rss.repository.sqlite

import net.matsudamper.mastodon.rss.repository.NewNoteFavourite
import net.matsudamper.mastodon.rss.repository.NoteFavouriteRepository
import net.matsudamper.mastodon.rss.repository.jooq.Tables.NOTES
import net.matsudamper.mastodon.rss.repository.jooq.Tables.NOTE_FAVOURITES
import net.matsudamper.mastodon.rss.repository.jooq.Tables.REMOTE_ACTORS
import net.matsudamper.mastodon.rss.shared.PublicNoteId
import org.jooq.impl.DSL

internal class SqliteNoteFavouriteRepository(
    private val jooq: SqliteJooq,
) : NoteFavouriteRepository {
    override fun add(favourite: NewNoteFavourite): Boolean = jooq.transaction { dsl ->
        // 配信していない投稿へのお気に入りは外部キーで弾かれる。例外で落とすと
        // inbox の処理の失敗として記録されるだけなので、先に見て false で返す
        val noteExists = dsl.fetchExists(
            DSL
                .selectOne()
                .from(NOTES)
                .where(NOTES.PUBLIC_ID.eq(favourite.notePublicId.value)),
        )
        if (noteExists.not()) return@transaction false

        // 数えるのと入れるのを同じトランザクションに入れる。分けると、お気に入りが
        // 並んだときに上限を越えた分まで入る
        val storedInNote = dsl
            .selectCount()
            .from(NOTE_FAVOURITES)
            .where(NOTE_FAVOURITES.NOTE_PUBLIC_ID.eq(favourite.notePublicId.value))
            .fetchOne(0, Int::class.java) ?: 0
        if (storedInNote >= MAX_FAVOURITES_PER_NOTE) return@transaction false

        val remoteActorId = RemoteActorRows.upsert(dsl = dsl, actor = favourite.actor, now = favourite.receivedAt)

        val inserted = dsl
            .insertInto(NOTE_FAVOURITES)
            .set(NOTE_FAVOURITES.NOTE_PUBLIC_ID, favourite.notePublicId.value)
            .set(NOTE_FAVOURITES.REMOTE_ACTOR_ID, remoteActorId)
            .set(NOTE_FAVOURITES.ACTIVITY_URI, favourite.activityUri)
            .set(NOTE_FAVOURITES.CREATED_AT, StoredInstant.format(favourite.receivedAt))
            .onConflictDoNothing()
            .execute()

        inserted > 0
    }

    /**
     * 取り消しで誰も指さなくなった相手の行も消す。押してすぐ取り消すのを
     * アクターを替えて繰り返されると、お気に入りの上限に当たらないまま行だけが増える
     */
    override fun removeByActivityUri(
        actorUri: String,
        activityUri: String,
    ): Boolean = jooq.transaction { dsl ->
        val removed = dsl
            .deleteFrom(NOTE_FAVOURITES)
            .where(NOTE_FAVOURITES.ACTIVITY_URI.eq(activityUri))
            .and(NOTE_FAVOURITES.REMOTE_ACTOR_ID.`in`(RemoteActorRows.id(actorUri)))
            .execute() > 0

        RemoteActorRows.deleteIfUnreferenced(dsl = dsl, actorUri = actorUri)
        removed
    }

    override fun removeByNote(
        notePublicId: PublicNoteId,
        actorUri: String,
    ): Boolean = jooq.transaction { dsl ->
        val removed = dsl
            .deleteFrom(NOTE_FAVOURITES)
            .where(NOTE_FAVOURITES.NOTE_PUBLIC_ID.eq(notePublicId.value))
            .and(NOTE_FAVOURITES.REMOTE_ACTOR_ID.`in`(RemoteActorRows.id(actorUri)))
            .execute() > 0

        RemoteActorRows.deleteIfUnreferenced(dsl = dsl, actorUri = actorUri)
        removed
    }

    override fun removeByActor(actorUri: String): Int = jooq.transaction { dsl ->
        dsl
            .deleteFrom(NOTE_FAVOURITES)
            .where(NOTE_FAVOURITES.REMOTE_ACTOR_ID.`in`(RemoteActorRows.id(actorUri)))
            .execute()
    }

    override fun findPublicKeyPem(actorUri: String): String? = jooq.withConnection { dsl ->
        dsl
            .select(REMOTE_ACTORS.PUBLIC_KEY_PEM)
            .from(REMOTE_ACTORS)
            .join(NOTE_FAVOURITES)
            .on(NOTE_FAVOURITES.REMOTE_ACTOR_ID.eq(REMOTE_ACTORS.ID))
            .where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri))
            .limit(1)
            .fetchOne(REMOTE_ACTORS.PUBLIC_KEY_PEM)
    }

    override fun countsByNotes(notePublicIds: Set<PublicNoteId>): Map<PublicNoteId, Int> {
        if (notePublicIds.isEmpty()) return mapOf()

        return jooq.withConnection { dsl ->
            dsl
                .select(NOTE_FAVOURITES.NOTE_PUBLIC_ID, DSL.count())
                .from(NOTE_FAVOURITES)
                .where(NOTE_FAVOURITES.NOTE_PUBLIC_ID.`in`(notePublicIds.map { it.value }))
                .groupBy(NOTE_FAVOURITES.NOTE_PUBLIC_ID)
                .fetch()
                .associate { PublicNoteId(it.value1()) to it.value2() }
        }
    }

    private companion object {
        /**
         * 相手はアクターをいくつでも作れるので、1 人 1 件に絞るだけでは
         * 人数ぶんだけ行が増える
         */
        const val MAX_FAVOURITES_PER_NOTE = 500
    }
}
