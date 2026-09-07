package net.matsudamper.mastodon.rss.repository.sqlite

import net.matsudamper.mastodon.rss.repository.FeedIcon
import net.matsudamper.mastodon.rss.repository.FeedIconRepository
import net.matsudamper.mastodon.rss.repository.entity.FeedId
import net.matsudamper.mastodon.rss.repository.jooq.Tables.FEED_ICONS

internal class SqliteFeedIconRepository(
    private val jooq: SqliteJooq,
) : FeedIconRepository {
    override fun find(feedId: FeedId): FeedIcon? = jooq.withConnection { dsl ->
        dsl
            .selectFrom(FEED_ICONS)
            .where(FEED_ICONS.FEED_ID.eq(feedId.value))
            .fetchOne()
            ?.let { record ->
                FeedIcon(
                    sourceUrl = record.sourceUrl!!,
                    contentType = record.contentType!!,
                    path = record.path!!,
                    fetchedAt = StoredInstant.parse(record.fetchedAt!!),
                    expiresAt = StoredInstant.parse(record.expiresAt!!),
                )
            }
    }

    override fun save(
        feedId: FeedId,
        icon: FeedIcon,
    ) {
        jooq.transaction { dsl ->
            dsl
                .insertInto(FEED_ICONS)
                .set(FEED_ICONS.FEED_ID, feedId.value)
                .set(FEED_ICONS.SOURCE_URL, icon.sourceUrl)
                .set(FEED_ICONS.CONTENT_TYPE, icon.contentType)
                .set(FEED_ICONS.PATH, icon.path)
                .set(FEED_ICONS.FETCHED_AT, StoredInstant.format(icon.fetchedAt))
                .set(FEED_ICONS.EXPIRES_AT, StoredInstant.format(icon.expiresAt))
                .onConflict(FEED_ICONS.FEED_ID)
                .doUpdate()
                .set(FEED_ICONS.SOURCE_URL, icon.sourceUrl)
                .set(FEED_ICONS.CONTENT_TYPE, icon.contentType)
                .set(FEED_ICONS.PATH, icon.path)
                .set(FEED_ICONS.FETCHED_AT, StoredInstant.format(icon.fetchedAt))
                .set(FEED_ICONS.EXPIRES_AT, StoredInstant.format(icon.expiresAt))
                .execute()
        }
    }

    override fun delete(feedId: FeedId) {
        jooq.transaction { dsl ->
            dsl
                .deleteFrom(FEED_ICONS)
                .where(FEED_ICONS.FEED_ID.eq(feedId.value))
                .execute()
        }
    }
}
