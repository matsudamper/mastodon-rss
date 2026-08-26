package net.matsudamper.mastodon.rss.repository

import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FeedRepositoryTest {
    private val tempDir: Path = createTempDirectory("mastodon-rss-feed-test")

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `フィードを追加して account_id で引ける`() {
        withRepositories { repositories ->
            val account = assertNotNull(repositories.accounts.add(username = "feed1", createdAt = CREATED_AT))

            val added = assertNotNull(
                repositories.feeds.add(
                    NewFeed(
                        accountId = account.id,
                        url = "https://example.com/feed.xml",
                        title = "サンプル",
                        siteUrl = "https://example.com/",
                        format = "Atom 1.0",
                        pollIntervalSeconds = 900,
                    ),
                ),
            )

            assertEquals("https://example.com/feed.xml", added.url)
            assertEquals(account.id, added.accountId)
            assertEquals(added, repositories.feeds.findByAccountId(account.id))
        }
    }

    @Test
    fun `同じ URL は二重登録できない`() {
        withRepositories { repositories ->
            val account1 = assertNotNull(repositories.accounts.add(username = "feed1", createdAt = CREATED_AT))
            val account2 = assertNotNull(repositories.accounts.add(username = "feed2", createdAt = CREATED_AT))

            repositories.feeds.add(
                NewFeed(
                    accountId = account1.id,
                    url = "https://example.com/feed.xml",
                    title = null,
                    siteUrl = null,
                    format = null,
                    pollIntervalSeconds = 900,
                ),
            )

            assertNull(
                repositories.feeds.add(
                    NewFeed(
                        accountId = account2.id,
                        url = "https://example.com/feed.xml",
                        title = null,
                        siteUrl = null,
                        format = null,
                        pollIntervalSeconds = 900,
                    ),
                ),
            )
        }
    }

    @Test
    fun `同じアカウントには二重登録できない`() {
        withRepositories { repositories ->
            val account = assertNotNull(repositories.accounts.add(username = "feed1", createdAt = CREATED_AT))

            repositories.feeds.add(
                NewFeed(
                    accountId = account.id,
                    url = "https://example.com/feed1.xml",
                    title = null,
                    siteUrl = null,
                    format = null,
                    pollIntervalSeconds = 900,
                ),
            )

            assertNull(
                repositories.feeds.add(
                    NewFeed(
                        accountId = account.id,
                        url = "https://example.com/feed2.xml",
                        title = null,
                        siteUrl = null,
                        format = null,
                        pollIntervalSeconds = 900,
                    ),
                ),
            )
        }
    }

    @Test
    fun `findDue は取得間隔を過ぎたものだけ返す`() {
        withRepositories { repositories ->
            val notFetched = repositories.addFeed(username = "feed1", url = "https://example.com/1.xml")
            val due = repositories.addFeed(username = "feed2", url = "https://example.com/2.xml")
            val notDue = repositories.addFeed(username = "feed3", url = "https://example.com/3.xml")

            val now = Instant.parse("2026-08-16T12:00:00Z")
            repositories.feeds.recordFetchSuccess(
                id = due.id,
                fetchedAt = now.minusSeconds(901),
                validators = FeedFetchValidators.NONE,
            )
            repositories.feeds.recordFetchSuccess(
                id = notDue.id,
                fetchedAt = now.minusSeconds(899),
                validators = FeedFetchValidators.NONE,
            )

            val found = repositories.feeds.findDue(now = now, limit = 10)

            // 一度も取っていないものが先。次に取得予定を過ぎたものが古い順
            assertEquals(listOf(notFetched.id, due.id), found.map { it.id })
        }
    }

    @Test
    fun `findDue は登録時の取り込みが済んでいないものを返さない`() {
        withRepositories { repositories ->
            val importing = repositories.addFeed(
                username = "feed1",
                url = "https://example.com/1.xml",
                initialImportDone = false,
            )
            val done = repositories.addFeed(username = "feed2", url = "https://example.com/2.xml")

            val found = repositories.feeds.findDue(now = CREATED_AT, limit = 10)

            assertEquals(listOf(done.id), found.map { it.id })

            repositories.feeds.markInitialImportDone(importing.id)

            assertEquals(2, repositories.feeds.findDue(now = CREATED_AT, limit = 10).size)
        }
    }

    @Test
    fun `findDue は取り込みが済んでいないものに枠を取られない`() {
        withRepositories { repositories ->
            repositories.addFeed(username = "feed1", url = "https://example.com/1.xml", initialImportDone = false)
            val done = repositories.addFeed(username = "feed2", url = "https://example.com/2.xml")

            assertEquals(listOf(done.id), repositories.feeds.findDue(now = CREATED_AT, limit = 1).map { it.id })
        }
    }

    @Test
    fun `findDue は limit で件数を抑える`() {
        withRepositories { repositories ->
            repositories.addFeed(username = "feed1", url = "https://example.com/1.xml")
            repositories.addFeed(username = "feed2", url = "https://example.com/2.xml")

            assertEquals(1, repositories.feeds.findDue(now = CREATED_AT, limit = 1).size)
            assertEquals(0, repositories.feeds.findDue(now = CREATED_AT, limit = 0).size)
        }
    }

    @Test
    fun `開き直しても残っている`() {
        val dbPath = tempDir.resolve("test.db")
        TestSchema.applyTo(dbPath)
        val config = DatabaseConfig(path = dbPath)

        val accountId = createRepositories(config).use { repositories ->
            val account = assertNotNull(repositories.accounts.add(username = "feed1", createdAt = CREATED_AT))
            repositories.feeds.add(
                NewFeed(
                    accountId = account.id,
                    url = "https://example.com/feed.xml",
                    title = "タイトル",
                    siteUrl = "https://example.com/",
                    format = "RSS 2.0",
                    pollIntervalSeconds = 900,
                ),
            )
            account.id
        }

        createRepositories(config).use {
            val feed = assertNotNull(it.feeds.findByAccountId(accountId))
            assertEquals("https://example.com/feed.xml", feed.url)
            assertEquals("タイトル", feed.title)
        }
    }

    /**
     * @param initialImportDone 登録時の取り込みが済んだことにするか。
     *   `findDue` はここが済んでいるものだけを返す
     */
    private fun Repositories.addFeed(
        username: String,
        url: String,
        initialImportDone: Boolean = true,
    ): Feed {
        val account = assertNotNull(accounts.add(username = username, createdAt = CREATED_AT))
        val feed = assertNotNull(
            feeds.add(
                NewFeed(
                    accountId = account.id,
                    url = url,
                    title = null,
                    siteUrl = null,
                    format = null,
                    pollIntervalSeconds = 900,
                ),
            ),
        )
        if (initialImportDone) {
            feeds.markInitialImportDone(feed.id)
        }
        return feed
    }

    private fun withRepositories(block: (Repositories) -> Unit) {
        val dbPath = tempDir.resolve("test.db")
        TestSchema.applyTo(dbPath)

        createRepositories(DatabaseConfig(path = dbPath)).use(block)
    }

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-08-16T01:02:03.123456Z")
    }
}
