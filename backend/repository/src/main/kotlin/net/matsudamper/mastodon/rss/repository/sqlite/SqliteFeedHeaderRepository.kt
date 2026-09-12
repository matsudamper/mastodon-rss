package net.matsudamper.mastodon.rss.repository.sqlite

import net.matsudamper.mastodon.rss.repository.FeedHeader
import net.matsudamper.mastodon.rss.repository.FeedHeaderRepository
import net.matsudamper.mastodon.rss.repository.entity.FeedId
import net.matsudamper.mastodon.rss.repository.jooq.Tables.FEED_HEADERS
import net.matsudamper.mastodon.rss.repository.jooq.tables.records.FeedHeadersRecord

internal class SqliteFeedHeaderRepository(
    private val jooq: SqliteJooq,
) : FeedHeaderRepository {
    override fun find(feedId: FeedId): FeedHeader? = jooq.withConnection { dsl ->
        dsl
            .selectFrom(FEED_HEADERS)
            .where(FEED_HEADERS.FEED_ID.eq(feedId.value))
            .fetchOne()
            ?.toFeedHeader()
    }

    override fun findByFeedIds(feedIds: Set<FeedId>): Map<FeedId, FeedHeader> {
        if (feedIds.isEmpty()) return mapOf()

        return jooq.withConnection { dsl ->
            dsl
                .selectFrom(FEED_HEADERS)
                .where(FEED_HEADERS.FEED_ID.`in`(feedIds.map { it.value }))
                .fetch()
                .associate { record ->
                    val feedId = FeedId(requireNotNull(record.feedId) { "feed_headers.feed_id が null" })
                    feedId to record.toFeedHeader()
                }
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

    private fun FeedHeadersRecord.toFeedHeader(): FeedHeader {
        val sourceUrl = requireNotNull(sourceUrl) { "feed_headers.source_url が null: feedId=$feedId" }
        val contentType = requireNotNull(contentType) { "feed_headers.content_type が null: feedId=$feedId" }
        val revision = requireNotNull(revision) { "feed_headers.revision が null: feedId=$feedId" }
        val path = requireNotNull(path) { "feed_headers.path が null: feedId=$feedId" }
        val fetchedAt = requireNotNull(fetchedAt) { "feed_headers.fetched_at が null: feedId=$feedId" }
        val expiresAt = requireNotNull(expiresAt) { "feed_headers.expires_at が null: feedId=$feedId" }
        return FeedHeader(
            sourceUrl = sourceUrl,
            contentType = contentType,
            revision = revision,
            path = path,
            fetchedAt = StoredInstant.parse(fetchedAt),
            expiresAt = StoredInstant.parse(expiresAt),
        )
    }
}
