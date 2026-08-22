package net.matsudamper.mastodon.rss.repository // pragma: allowlist secret

import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

            val added = repositories.feeds.add(
                NewFeed(
                    accountId = account.id,
                    url = "https://example.com/feed.xml",
                    title = "サンプル",
                    siteUrl = "https://example.com/",
                    format = "Atom 1.0",
                    pollIntervalSeconds = 900,
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

            assertFailsWith<IllegalStateException> {
                repositories.feeds.add(
                    NewFeed(
                        accountId = account2.id,
                        url = "https://example.com/feed.xml",
                        title = null,
                        siteUrl = null,
                        format = null,
                        pollIntervalSeconds = 900,
                    ),
                )
            }
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

            assertFailsWith<IllegalStateException> {
                repositories.feeds.add(
                    NewFeed(
                        accountId = account.id,
                        url = "https://example.com/feed2.xml",
                        title = null,
                        siteUrl = null,
                        format = null,
                        pollIntervalSeconds = 900,
                    ),
                )
            }
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

    private fun withRepositories(block: (Repositories) -> Unit) {
        val dbPath = tempDir.resolve("test.db")
        TestSchema.applyTo(dbPath)

        createRepositories(DatabaseConfig(path = dbPath)).use(block)
    }

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-08-16T01:02:03.123456Z")
    }
}
