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
        val storedId = id.value.toStoredId() ?: return@transaction null

        dsl
            .selectFrom(FEEDS)
            .where(FEEDS.ID.eq(storedId))
            .fetchOne()
            ?.toFeed()
    }

    override fun findByAccountId(accountId: AccountId): Feed? = jooq.transaction { dsl ->
        dsl.findByAccountId(accountId)
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

        // 次の取得予定は last_fetched_at に poll_interval_seconds を足した時刻。
        // TEXT で持っている時刻に秒を足す比較は SQLite に任せられないので、読んでから絞る
        dsl
            .selectFrom(FEEDS)
            .fetch()
            .map { it.toFeed() }
            .filter { it.isDue(now) }
            .sortedBy { it.fetch.lastFetchedAt ?: Instant.MIN }
            .take(limit)
    }

    override fun add(feed: NewFeed): Feed? = jooq.transaction { dsl ->
        // UNIQUE 制約違反を捕まえる形にすると、他の理由で落ちたときと区別が付かない。
        // 書き込みは接続 1 本に直列化されているので、同じトランザクションで
        // 見てから入れれば取りこぼさない
        val storedAccountId = feed.accountId.value.toStoredId() ?: return@transaction null
        if (dsl.findByAccountId(feed.accountId) != null) return@transaction null
        if (dsl.findByUrl(feed.url) != null) return@transaction null

        val createdAt = Instant.now()
        val id = dsl
            .insertInto(FEEDS)
            .set(FEEDS.ACCOUNT_ID, storedAccountId)
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
            val storedId = id.value.toStoredId() ?: return@transaction

            dsl
                .update(FEEDS)
                .set(FEEDS.TITLE, title)
                .set(FEEDS.SITE_URL, siteUrl)
                .set(FEEDS.FORMAT, format)
                .where(FEEDS.ID.eq(storedId))
                .execute()
        }
    }

    override fun recordFetchSuccess(
        id: FeedId,
        fetchedAt: Instant,
        validators: FeedFetchValidators,
    ) {
        jooq.transaction { dsl ->
            val storedId = id.value.toStoredId() ?: return@transaction

            dsl
                .update(FEEDS)
                .set(FEEDS.LAST_FETCHED_AT, StoredInstant.format(fetchedAt))
                .set(FEEDS.LAST_SUCCEEDED_AT, StoredInstant.format(fetchedAt))
                .set(FEEDS.LAST_ERROR, null as String?)
                .set(FEEDS.ETAG, validators.etag)
                .set(FEEDS.LAST_MODIFIED, validators.lastModified)
                .where(FEEDS.ID.eq(storedId))
                .execute()
        }
    }

    override fun recordFetchFailure(
        id: FeedId,
        fetchedAt: Instant,
        error: String,
    ) {
        jooq.transaction { dsl ->
            val storedId = id.value.toStoredId() ?: return@transaction

            dsl
                .update(FEEDS)
                .set(FEEDS.LAST_FETCHED_AT, StoredInstant.format(fetchedAt))
                .set(FEEDS.LAST_ERROR, error)
                .where(FEEDS.ID.eq(storedId))
                .execute()
        }
    }

    override fun markInitialImportDone(id: FeedId) {
        jooq.transaction { dsl ->
            val storedId = id.value.toStoredId() ?: return@transaction

            dsl
                .update(FEEDS)
                .set(FEEDS.INITIAL_IMPORT_DONE, 1)
                .where(FEEDS.ID.eq(storedId))
                .execute()
        }
    }

    override fun delete(id: FeedId) {
        jooq.transaction { dsl ->
            val storedId = id.value.toStoredId() ?: return@transaction

            dsl
                .deleteFrom(FEEDS)
                .where(FEEDS.ID.eq(storedId))
                .execute()
        }
    }

    private fun Feed.isDue(now: Instant): Boolean {
        val lastFetchedAt = fetch.lastFetchedAt ?: return true
        return lastFetchedAt.plusSeconds(pollIntervalSeconds) <= now
    }

    private fun org.jooq.DSLContext.findByAccountId(accountId: AccountId): Feed? {
        val storedId = accountId.value.toStoredId() ?: return null

        return selectFrom(FEEDS)
            .where(FEEDS.ACCOUNT_ID.eq(storedId))
            .fetchOne()
            ?.toFeed()
    }

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
