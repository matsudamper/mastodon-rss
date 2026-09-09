package net.matsudamper.mastodon.rss.repository.sqlite

import net.matsudamper.mastodon.rss.repository.FeedHeader
import net.matsudamper.mastodon.rss.repository.FeedHeaderRepository
import net.matsudamper.mastodon.rss.repository.entity.FeedId
import net.matsudamper.mastodon.rss.repository.jooq.Tables.FEED_HEADERS

internal class SqliteFeedHeaderRepository(
    private val jooq: SqliteJooq,
) : FeedHeaderRepository {
    override fun find(feedId: FeedId): FeedHeader? = jooq.withConnection { dsl ->
        dsl
            .selectFrom(FEED_HEADERS)
            .where(FEED_HEADERS.FEED_ID.eq(feedId.value))
            .fetchOne()
            ?.let { record ->
                val sourceUrl = requireNotNull(record.sourceUrl) { "feed_headers.source_url が null: feedId=${feedId.value}" }
                val contentType = requireNotNull(record.contentType) { "feed_headers.content_type が null: feedId=${feedId.value}" }
                val revision = requireNotNull(record.revision) { "feed_headers.revision が null: feedId=${feedId.value}" }
                val path = requireNotNull(record.path) { "feed_headers.path が null: feedId=${feedId.value}" }
                val fetchedAt = requireNotNull(record.fetchedAt) { "feed_headers.fetched_at が null: feedId=${feedId.value}" }
                val expiresAt = requireNotNull(record.expiresAt) { "feed_headers.expires_at が null: feedId=${feedId.value}" }
                FeedHeader(
                    sourceUrl = sourceUrl,
                    contentType = contentType,
                    revision = revision,
                    path = path,
                    fetchedAt = StoredInstant.parse(fetchedAt),
                    expiresAt = StoredInstant.parse(expiresAt),
                )
            }
    }

    override fun save(
        feedId: FeedId,
        header: FeedHeader,
    ) {
        jooq.transaction { dsl ->
            dsl
                .insertInto(FEED_HEADERS)
                .set(FEED_HEADERS.FEED_ID, feedId.value)
                .set(FEED_HEADERS.SOURCE_URL, header.sourceUrl)
                .set(FEED_HEADERS.CONTENT_TYPE, header.contentType)
                .set(FEED_HEADERS.REVISION, header.revision)
                .set(FEED_HEADERS.PATH, header.path)
                .set(FEED_HEADERS.FETCHED_AT, StoredInstant.format(header.fetchedAt))
                .set(FEED_HEADERS.EXPIRES_AT, StoredInstant.format(header.expiresAt))
                .onConflict(FEED_HEADERS.FEED_ID)
                .doUpdate()
                .set(FEED_HEADERS.SOURCE_URL, header.sourceUrl)
                .set(FEED_HEADERS.CONTENT_TYPE, header.contentType)
                .set(FEED_HEADERS.REVISION, header.revision)
                .set(FEED_HEADERS.PATH, header.path)
                .set(FEED_HEADERS.FETCHED_AT, StoredInstant.format(header.fetchedAt))
                .set(FEED_HEADERS.EXPIRES_AT, StoredInstant.format(header.expiresAt))
                .execute()
        }
    }

    override fun delete(feedId: FeedId) {
        jooq.transaction { dsl ->
            dsl
                .deleteFrom(FEED_HEADERS)
                .where(FEED_HEADERS.FEED_ID.eq(feedId.value))
                .execute()
        }
    }
}
