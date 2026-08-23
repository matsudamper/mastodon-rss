package net.matsudamper.mastodon.rss.repository.sqlite

import java.time.Instant
import net.matsudamper.mastodon.rss.repository.AccountId
import net.matsudamper.mastodon.rss.repository.Feed
import net.matsudamper.mastodon.rss.repository.FeedFetchStatus
import net.matsudamper.mastodon.rss.repository.FeedFetchValidators
import net.matsudamper.mastodon.rss.repository.FeedId
import net.matsudamper.mastodon.rss.repository.FeedRepository
import net.matsudamper.mastodon.rss.repository.NewFeed
import net.matsudamper.mastodon.rss.repository.jooq.Tables.FEEDS
import net.matsudamper.mastodon.rss.repository.jooq.tables.records.FeedsRecord

internal class SqliteFeedRepository(
    private val jooq: SqliteJooq,
) : FeedRepository {
    override fun list(): List<Feed> = jooq.transaction { dsl ->
        dsl
            .selectFrom(FEEDS)
            .orderBy(FEEDS.CREATED_AT, FEEDS.ID)
            .fetch()
            .map { it.toFeed() }
    }

    override fun find(id: FeedId): Feed? = jooq.transaction { dsl ->
        dsl
            .selectFrom(FEEDS)
            .where(FEEDS.ID.eq(id.value.toInt()))
            .fetchOne()
            ?.toFeed()
    }

    override fun findByAccountId(accountId: AccountId): Feed? = jooq.transaction { dsl ->
        dsl
            .selectFrom(FEEDS)
            .where(FEEDS.ACCOUNT_ID.eq(accountId.value.toInt()))
            .fetchOne()
            ?.toFeed()
    }

    override fun findByUrl(url: String): Feed? = jooq.transaction { dsl ->
        dsl
            .selectFrom(FEEDS)
            .where(FEEDS.URL.eq(url))
            .fetchOne()
            ?.toFeed()
    }

    override fun findDue(
        now: Instant,
        limit: Int,
    ): List<Feed> = jooq.transaction { dsl ->
        if (limit <= 0) return@transaction emptyList()

        dsl
            .selectFrom(FEEDS)
            .orderBy(FEEDS.CREATED_AT, FEEDS.ID)
            .limit(limit)
            .fetch()
            .map { it.toFeed() }
    }

    override fun add(feed: NewFeed): Feed = jooq.transaction { dsl ->
        check(dsl.findByAccountId(feed.accountId) == null) {
            "同じアカウントにフィードが既にある: accountId=${feed.accountId.value}"
        }
        check(dsl.findByUrl(feed.url) == null) {
            "同じ URL のフィードが既にある: url=${feed.url}"
        }

        val createdAt = Instant.now()
        val id = dsl
            .insertInto(FEEDS)
            .set(FEEDS.ACCOUNT_ID, feed.accountId.value.toInt())
            .set(FEEDS.URL, feed.url)
            .set(FEEDS.TITLE, feed.title)
            .set(FEEDS.SITE_URL, feed.siteUrl)
            .set(FEEDS.FORMAT, feed.format)
            .set(FEEDS.POLL_INTERVAL_SECONDS, feed.pollIntervalSeconds.toInt())
            .set(FEEDS.INITIAL_IMPORT_DONE, 0)
            .set(FEEDS.CREATED_AT, StoredInstant.format(createdAt))
            .returning(FEEDS.ID)
            .fetchOne()
            ?.id
            ?.toLong()
            ?: error("フィードの追加に失敗した")

        Feed(
            id = FeedId(id),
            accountId = feed.accountId,
            url = feed.url,
            title = feed.title,
            siteUrl = feed.siteUrl,
            format = feed.format,
            pollIntervalSeconds = feed.pollIntervalSeconds,
            fetch = FeedFetchStatus(
                validators = FeedFetchValidators.NONE,
                lastFetchedAt = null,
                lastSucceededAt = null,
                lastError = null,
            ),
            initialImportDone = false,
            createdAt = createdAt,
        )
    }

    override fun updateMetadata(
        id: FeedId,
        title: String?,
        siteUrl: String?,
        format: String?,
    ) {
        jooq.transaction { dsl ->
            dsl
                .update(FEEDS)
                .set(FEEDS.TITLE, title)
                .set(FEEDS.SITE_URL, siteUrl)
                .set(FEEDS.FORMAT, format)
                .where(FEEDS.ID.eq(id.value.toInt()))
                .execute()
        }
    }

    override fun recordFetchSuccess(
        id: FeedId,
        fetchedAt: Instant,
        validators: FeedFetchValidators,
    ) {
        jooq.transaction { dsl ->
            dsl
                .update(FEEDS)
                .set(FEEDS.LAST_FETCHED_AT, StoredInstant.format(fetchedAt))
                .set(FEEDS.LAST_SUCCEEDED_AT, StoredInstant.format(fetchedAt))
                .set(FEEDS.LAST_ERROR, null as String?)
                .set(FEEDS.ETAG, validators.etag)
                .set(FEEDS.LAST_MODIFIED, validators.lastModified)
                .where(FEEDS.ID.eq(id.value.toInt()))
                .execute()
        }
    }

    override fun recordFetchFailure(
        id: FeedId,
        fetchedAt: Instant,
        error: String,
    ) {
        jooq.transaction { dsl ->
            dsl
                .update(FEEDS)
                .set(FEEDS.LAST_FETCHED_AT, StoredInstant.format(fetchedAt))
                .set(FEEDS.LAST_ERROR, error)
                .where(FEEDS.ID.eq(id.value.toInt()))
                .execute()
        }
    }

    override fun markInitialImportDone(id: FeedId) {
        jooq.transaction { dsl ->
            dsl
                .update(FEEDS)
                .set(FEEDS.INITIAL_IMPORT_DONE, 1)
                .where(FEEDS.ID.eq(id.value.toInt()))
                .execute()
        }
    }

    override fun delete(id: FeedId) {
        jooq.transaction { dsl ->
            dsl
                .deleteFrom(FEEDS)
                .where(FEEDS.ID.eq(id.value.toInt()))
                .execute()
        }
    }

    private fun org.jooq.DSLContext.findByAccountId(accountId: AccountId): Feed? = selectFrom(FEEDS)
        .where(FEEDS.ACCOUNT_ID.eq(accountId.value.toInt()))
        .fetchOne()
        ?.toFeed()

    private fun org.jooq.DSLContext.findByUrl(url: String): Feed? = selectFrom(FEEDS)
        .where(FEEDS.URL.eq(url))
        .fetchOne()
        ?.toFeed()

    private fun FeedsRecord.toFeed(): Feed = Feed(
        id = FeedId(id!!.toLong()),
        accountId = AccountId(accountId!!.toLong()),
        url = url!!,
        title = title,
        siteUrl = siteUrl,
        format = format,
        pollIntervalSeconds = pollIntervalSeconds!!.toLong(),
        fetch = FeedFetchStatus(
            validators = FeedFetchValidators(
                etag = etag,
                lastModified = lastModified,
            ),
            lastFetchedAt = lastFetchedAt?.let(StoredInstant::parse),
            lastSucceededAt = lastSucceededAt?.let(StoredInstant::parse),
            lastError = lastError,
        ),
        initialImportDone = initialImportDone != 0,
        createdAt = StoredInstant.parse(createdAt!!),
    )
}
