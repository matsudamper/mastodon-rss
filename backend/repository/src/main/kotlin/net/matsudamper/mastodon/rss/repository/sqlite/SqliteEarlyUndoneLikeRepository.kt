package net.matsudamper.mastodon.rss.repository.sqlite

import java.time.Instant
import net.matsudamper.mastodon.rss.repository.EarlyUndoneLikeRepository
import net.matsudamper.mastodon.rss.repository.jooq.Tables.EARLY_UNDONE_LIKES

internal class SqliteEarlyUndoneLikeRepository(
    private val jooq: SqliteJooq,
) : EarlyUndoneLikeRepository {
    /**
     * 期限切れの行もここで消す。相手は id をいくらでも作れるので、読み直されない行が溜まる
     */
    override fun remember(
        actorUri: String,
        activityUri: String,
        expiresAt: Instant,
    ) {
        jooq.transaction { dsl ->
            dsl
                .deleteFrom(EARLY_UNDONE_LIKES)
                .where(EARLY_UNDONE_LIKES.EXPIRES_AT.lt(StoredInstant.format(Instant.now())))
                .execute()

            dsl
                .insertInto(EARLY_UNDONE_LIKES)
                .set(EARLY_UNDONE_LIKES.ACTOR_URI, actorUri)
                .set(EARLY_UNDONE_LIKES.ACTIVITY_URI, activityUri)
                .set(EARLY_UNDONE_LIKES.EXPIRES_AT, StoredInstant.format(expiresAt))
                .onConflict(EARLY_UNDONE_LIKES.ACTOR_URI, EARLY_UNDONE_LIKES.ACTIVITY_URI)
                .doUpdate()
                .set(EARLY_UNDONE_LIKES.EXPIRES_AT, StoredInstant.format(expiresAt))
                .execute()
        }
    }

    override fun isRemembered(
        actorUri: String,
        activityUri: String,
        now: Instant,
    ): Boolean = jooq.withConnection { dsl ->
        dsl.fetchExists(
            dsl
                .selectOne()
                .from(EARLY_UNDONE_LIKES)
                .where(EARLY_UNDONE_LIKES.ACTOR_URI.eq(actorUri))
                .and(EARLY_UNDONE_LIKES.ACTIVITY_URI.eq(activityUri))
                .and(EARLY_UNDONE_LIKES.EXPIRES_AT.gt(StoredInstant.format(now))),
        )
    }
}
