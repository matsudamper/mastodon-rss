package net.matsudamper.mastodon.rss.repository.sqlite

import java.time.Instant
import net.matsudamper.mastodon.rss.repository.Feed
import net.matsudamper.mastodon.rss.repository.FeedFetchStatus
import net.matsudamper.mastodon.rss.repository.FeedFetchValidators
import net.matsudamper.mastodon.rss.repository.FeedRepository
import net.matsudamper.mastodon.rss.repository.NewFeed
import net.matsudamper.mastodon.rss.repository.entity.FeedId
import net.matsudamper.mastodon.rss.repository.jooq.Tables.FEEDS
import net.matsudamper.mastodon.rss.repository.jooq.tables.records.FeedsRecord
import net.matsudamper.mastodon.rss.shared.AccountId

internal class SqliteFeedRepository(
    private val jooq: SqliteJooq,
) : FeedRepository {
    override fun list(): List<Feed> = jooq.withConnection { dsl ->
        dsl
            .selectFrom(FEEDS)
            .orderBy(FEEDS.CREATED_AT, FEEDS.ID)
            .fetch()
            .map { it.toFeed() }
    }

    override fun find(id: FeedId): Feed? = jooq.withConnection { dsl ->

        dsl
            .selectFrom(FEEDS)
            .where(FEEDS.ID.eq(id.value))
            .fetchOne()
            ?.toFeed()
    }

    override fun findByAccountId(accountId: AccountId): Feed? = jooq.withConnection { dsl ->
        dsl.findByAccountId(accountId)
    }

    override fun findByAccountIds(accountIds: Set<AccountId>): Map<AccountId, Feed> {
        if (accountIds.isEmpty()) return emptyMap()

        return jooq.withConnection { dsl ->
            dsl
                .selectFrom(FEEDS)
                .where(FEEDS.ACCOUNT_ID.`in`(accountIds.map { it.value }))
                .fetch()
                .map { it.toFeed() }
                .associateBy { it.accountId }
        }
    }

    override fun findByUrl(url: String): Feed? = jooq.withConnection { dsl ->
        dsl
            .selectFrom(FEEDS)
            .where(FEEDS.URL.eq(url))
            .fetchOne()
            ?.toFeed()
    }

    override fun findDue(
        now: Instant,
        limit: Int,
    ): List<Feed> = jooq.withConnection { dsl ->
        if (limit <= 0) return@withConnection emptyList()

        // 次の取得予定は last_fetched_at に poll_interval_seconds を足した時刻。
        // TEXT で持っている時刻に秒を足す比較は SQLite に任せられないので、読んでから絞る
        dsl
            .selectFrom(FEEDS)
            .fetch()
            .map { it.toFeed() }
            .filter { it.isDue(now) && (it.initialImportDone || it.registrationTimedOut(now)) }
            .sortedBy { it.fetch.lastFetchedAt ?: Instant.MIN }
            .take(limit)
    }

    override fun add(feed: NewFeed): Feed? = jooq.transaction { dsl ->
        // UNIQUE 制約違反を捕まえる形にすると、他の理由で落ちたときと区別が付かない。
        // 書き込みは接続 1 本に直列化されているので、同じトランザクションで
        // 見てから入れれば取りこぼさない
        if (dsl.findByAccountId(feed.accountId) != null) return@transaction null
        if (dsl.findByUrl(feed.url) != null) return@transaction null

        val createdAt = Instant.now()
        val id = dsl
            .insertInto(FEEDS)
            .set(FEEDS.ACCOUNT_ID, feed.accountId.value)
            .set(FEEDS.URL, feed.url)
            .set(FEEDS.TITLE, feed.title)
            .set(FEEDS.SITE_URL, feed.siteUrl)
            .set(FEEDS.FORMAT, feed.format)
            .set(FEEDS.POLL_INTERVAL_SECONDS, feed.pollIntervalSeconds)
            .set(FEEDS.INITIAL_IMPORT_DONE, 0L)
            .set(FEEDS.CREATED_AT, StoredInstant.format(createdAt))
            .returning(FEEDS.ID)
            .fetchOne()
            ?.id
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
                .where(FEEDS.ID.eq(id.value))
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
                .where(FEEDS.ID.eq(id.value))
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
                .where(FEEDS.ID.eq(id.value))
                .execute()
        }
    }

    override fun markInitialImportDone(id: FeedId) {
        jooq.transaction { dsl ->

            dsl
                .update(FEEDS)
                .set(FEEDS.INITIAL_IMPORT_DONE, 1L)
                .where(FEEDS.ID.eq(id.value))
                .execute()
        }
    }

    override fun delete(id: FeedId) {
        jooq.transaction { dsl ->

            dsl
                .deleteFrom(FEEDS)
                .where(FEEDS.ID.eq(id.value))
                .execute()
        }
    }

    /**
     * 登録の取り込みが、間隔を過ぎても終わっていない。
     *
     * 登録は取得を終えてから間を置かずに済む。過ぎているなら途中で終わっている
     */
    private fun Feed.registrationTimedOut(now: Instant): Boolean = createdAt.plusSeconds(pollIntervalSeconds) <= now

    private fun Feed.isDue(now: Instant): Boolean {
        val lastFetchedAt = fetch.lastFetchedAt ?: return true
        return lastFetchedAt.plusSeconds(pollIntervalSeconds) <= now
    }

    private fun org.jooq.DSLContext.findByAccountId(accountId: AccountId): Feed? {
        return selectFrom(FEEDS)
            .where(FEEDS.ACCOUNT_ID.eq(accountId.value))
            .fetchOne()
            ?.toFeed()
    }

    private fun org.jooq.DSLContext.findByUrl(url: String): Feed? = selectFrom(FEEDS)
        .where(FEEDS.URL.eq(url))
        .fetchOne()
        ?.toFeed()

    private fun FeedsRecord.toFeed(): Feed = Feed(
        id = FeedId(id!!),
        accountId = AccountId(accountId!!),
        url = url!!,
        title = title,
        siteUrl = siteUrl,
        format = format,
        pollIntervalSeconds = pollIntervalSeconds!!,
        fetch = FeedFetchStatus(
            validators = FeedFetchValidators(
                etag = etag,
                lastModified = lastModified,
            ),
            lastFetchedAt = lastFetchedAt?.let(StoredInstant::parse),
            lastSucceededAt = lastSucceededAt?.let(StoredInstant::parse),
            lastError = lastError,
        ),
        initialImportDone = initialImportDone != 0L,
        createdAt = StoredInstant.parse(createdAt!!),
    )
}
