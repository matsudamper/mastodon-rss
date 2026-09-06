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

class FeedIconRepositoryTest {
    private val tempDir: Path = createTempDirectory("mastodon-rss-feed-icon-test")

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `保存して読み戻せる`() {
        withRepositories { repositories ->
            val feed = repositories.addFeed()

            repositories.feedIcons.save(feed.id, icon())

            assertEquals(icon(), repositories.feedIcons.find(feed.id))
        }
    }

    @Test
    fun `フィードに 1 つだけ持つ`() {
        withRepositories { repositories ->
            val feed = repositories.addFeed()
            repositories.feedIcons.save(feed.id, icon())

            repositories.feedIcons.save(
                feed.id,
                icon().copy(
                    sourceUrl = "https://example.com/icon2.png",
                    path = "2",
                    expiresAt = EXPIRES_AT.plusSeconds(60),
                ),
            )

            val stored = assertNotNull(repositories.feedIcons.find(feed.id))
            assertEquals("https://example.com/icon2.png", stored.sourceUrl)
            assertEquals("2", stored.path)
            assertEquals(EXPIRES_AT.plusSeconds(60), stored.expiresAt)
        }
    }

    @Test
    fun `消せる`() {
        withRepositories { repositories ->
            val feed = repositories.addFeed()
            repositories.feedIcons.save(feed.id, icon())

            repositories.feedIcons.delete(feed.id)

            assertNull(repositories.feedIcons.find(feed.id))
        }
    }

    @Test
    fun `フィードを消すと一緒に消える`() {
        withRepositories { repositories ->
            val feed = repositories.addFeed()
            repositories.feedIcons.save(feed.id, icon())

            repositories.feeds.delete(feed.id)

            assertNull(repositories.feedIcons.find(feed.id))
        }
    }

    private fun Repositories.addFeed(): Feed {
        val account = assertNotNull(accounts.add(username = "feed1", createdAt = CREATED_AT))
        return assertNotNull(
            feeds.add(
                NewFeed(
                    accountId = account.id,
                    url = "https://example.com/feed.xml",
                    title = "サンプル",
                    siteUrl = "https://example.com/",
                    format = "RSS 2.0",
                    iconUrl = "https://example.com/icon.png",
                    pollIntervalSeconds = 900,
                ),
            ),
        )
    }

    private fun icon(): FeedIcon = FeedIcon(
        sourceUrl = "https://example.com/icon.png",
        contentType = "image/png",
        path = "1",
        fetchedAt = CREATED_AT,
        expiresAt = EXPIRES_AT,
    )

    private fun withRepositories(block: (Repositories) -> Unit) {
        val dbPath = tempDir.resolve("test.db")
        TestSchema.applyTo(dbPath)
        createRepositories(DatabaseConfig(path = dbPath)).use(block)
    }

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-08-16T01:02:03Z")
        val EXPIRES_AT: Instant = Instant.parse("2026-08-16T02:02:03Z")
    }
}
