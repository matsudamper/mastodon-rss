package net.matsudamper.mastodon.rss.logic

import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import net.matsudamper.mastodon.rss.FakeRepositories
import net.matsudamper.mastodon.rss.repository.FeedIcon
import net.matsudamper.mastodon.rss.repository.NewFeed
import net.matsudamper.mastodon.rss.repository.entity.FeedId

// /users/{name}/icon が返す中身。ここからは配信元へ取りに行かない。
class ActorIconServiceTest {
    private val tempDir: Path = createTempDirectory("mastodon-rss-icon-test")
    private val store = FeedIconStore(tempDir)

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `置いてあるものを返す`() =
        runTest {
            val repositories = FakeRepositories()
            val feedId = repositories.addFeed(iconUrl = ICON_URL)
            repositories.store(feedId = feedId, sourceUrl = ICON_URL, expiresAt = Instant.now().plusSeconds(60))

            val icon = assertNotNull(serviceOf(repositories).find(USERNAME))

            assertEquals(BYTES.toList(), icon.bytes.toList())
            assertTrue(icon.cacheFor <= Duration.ofSeconds(60), "実際の持たせる時間: ${icon.cacheFor}")
        }

    @Test
    fun `期限が切れていても返すが持たせない`() =
        runTest {
            val repositories = FakeRepositories()
            val feedId = repositories.addFeed(iconUrl = ICON_URL)
            repositories.store(feedId = feedId, sourceUrl = ICON_URL, expiresAt = Instant.now().minusSeconds(1))

            val icon = assertNotNull(serviceOf(repositories).find(USERNAME))

            assertEquals(Duration.ZERO, icon.cacheFor)
        }

    @Test
    fun `取得元が変わったものは返さない`() =
        runTest {
            val repositories = FakeRepositories()
            val feedId = repositories.addFeed(iconUrl = OTHER_ICON_URL)
            repositories.store(feedId = feedId, sourceUrl = ICON_URL, expiresAt = Instant.now().plusSeconds(60))

            assertNull(serviceOf(repositories).find(USERNAME))
        }

    @Test
    fun `まだ取れていないアカウントは返さない`() =
        runTest {
            val repositories = FakeRepositories()
            repositories.addFeed(iconUrl = ICON_URL)

            assertNull(serviceOf(repositories).find(USERNAME))
        }

    @Test
    fun `アイコンを名乗っていないフィードは返さない`() =
        runTest {
            val repositories = FakeRepositories()
            val feedId = repositories.addFeed(iconUrl = null)
            repositories.store(feedId = feedId, sourceUrl = ICON_URL, expiresAt = Instant.now().plusSeconds(60))

            assertNull(serviceOf(repositories).find(USERNAME))
        }

    private fun FakeRepositories.store(
        feedId: FeedId,
        sourceUrl: String,
        expiresAt: Instant,
    ) {
        feedIcons.save(
            feedId = feedId,
            icon = FeedIcon(
                sourceUrl = sourceUrl,
                contentType = "image/png",
                path = store.write(feedId = feedId, bytes = BYTES),
                fetchedAt = Instant.now(),
                expiresAt = expiresAt,
            ),
        )
    }

    private fun FakeRepositories.addFeed(iconUrl: String?): FeedId {
        val account = assertNotNull(accounts.add(username = USERNAME, createdAt = Instant.now()))
        val feed = assertNotNull(
            feeds.add(
                NewFeed(
                    accountId = account.id,
                    url = FEED_URL,
                    title = "サンプル",
                    siteUrl = SITE_URL,
                    format = "RSS 2.0",
                    iconUrl = iconUrl,
                    pollIntervalSeconds = 900,
                ),
            ),
        )
        return feed.id
    }

    private fun serviceOf(repositories: FakeRepositories): ActorIconService = ActorIconService(
        accounts = repositories.accounts,
        feeds = repositories.feeds,
        icons = repositories.feedIcons,
        store = store,
    )

    private companion object {
        const val USERNAME = "feed1"
        const val FEED_URL = "https://example.com/feed.xml"
        const val SITE_URL = "https://example.com/"
        const val ICON_URL = "https://example.com/icon.png"
        const val OTHER_ICON_URL = "https://example.com/icon2.png"
        val BYTES = "PNG".toByteArray()
    }
}
