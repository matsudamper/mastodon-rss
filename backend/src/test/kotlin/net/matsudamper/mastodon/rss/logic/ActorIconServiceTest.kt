package net.matsudamper.mastodon.rss.logic

import java.net.InetAddress
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import net.matsudamper.mastodon.rss.FakeFeedIconRepository
import net.matsudamper.mastodon.rss.FakeRepositories
import net.matsudamper.mastodon.rss.feed.IconFetchService
import net.matsudamper.mastodon.rss.repository.FeedIcon
import net.matsudamper.mastodon.rss.repository.NewFeed
import net.matsudamper.mastodon.rss.repository.entity.FeedId

// /users/{name}/icon は無認証で誰でも叩ける。叩かれた数だけ配信元へ出ていかないことを見る。
class ActorIconServiceTest {
    private val tempDir: Path = createTempDirectory("mastodon-rss-icon-test")

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `2 回目は置いてあるものを出す`() =
        runTest {
            val repositories = FakeRepositories()
            val feedId = repositories.addFeed(iconUrl = ICON_URL)
            val engine = imageEngine()
            val service = serviceOf(repositories, engine)

            assertEquals(BYTES.toList(), assertNotNull(service.find(USERNAME)).bytes.toList())
            assertEquals(BYTES.toList(), assertNotNull(service.find(USERNAME)).bytes.toList())

            assertEquals(1, engine.requestHistory.size)
            assertEquals(ICON_URL, assertNotNull(repositories.feedIcons.find(feedId)).sourceUrl)
        }

    @Test
    fun `期限が切れたら取り直す`() =
        runTest {
            val repositories = FakeRepositories()
            val feedId = repositories.addFeed(iconUrl = ICON_URL)
            val engine = imageEngine()
            val service = serviceOf(repositories, engine)

            service.find(USERNAME)
            repositories.feedIcons.expire(feedId)
            service.find(USERNAME)

            assertEquals(2, engine.requestHistory.size)
        }

    @Test
    fun `取得元が変わったら取り直す`() =
        runTest {
            val repositories = FakeRepositories()
            val feedId = repositories.addFeed(iconUrl = ICON_URL)
            val engine = imageEngine()
            val service = serviceOf(repositories, engine)

            service.find(USERNAME)
            repositories.feeds.updateMetadata(
                id = feedId,
                title = "サンプル",
                siteUrl = SITE_URL,
                format = "RSS 2.0",
                iconUrl = OTHER_ICON_URL,
            )
            service.find(USERNAME)

            assertEquals(
                listOf("/icon.png", "/icon2.png"),
                engine.requestHistory.map { it.url.encodedPath },
            )
        }

    @Test
    fun `配信元が言う期限に従う`() =
        runTest {
            val repositories = FakeRepositories()
            val feedId = repositories.addFeed(iconUrl = ICON_URL)
            val engine = MockEngine {
                respond(
                    content = BYTES.decodeToString(),
                    headers = headersOf(
                        "Content-Type" to listOf("image/png"),
                        "Cache-Control" to listOf("public, max-age=60"),
                    ),
                )
            }

            val before = Instant.now()
            serviceOf(repositories, engine).find(USERNAME)

            val freshFor = Duration.between(before, assertNotNull(repositories.feedIcons.find(feedId)).expiresAt)
            assertTrue(freshFor <= Duration.ofSeconds(60), "実際の期限: $freshFor")
        }

    @Test
    fun `取り直せなければ期限が切れていても置いてあるものを出す`() =
        runTest {
            val repositories = FakeRepositories()
            val feedId = repositories.addFeed(iconUrl = ICON_URL)
            var served = false
            val engine = MockEngine {
                if (served) {
                    respond(content = "", status = HttpStatusCode.InternalServerError)
                } else {
                    served = true
                    respond(content = BYTES.decodeToString(), headers = headersOf("Content-Type", "image/png"))
                }
            }
            val service = serviceOf(repositories, engine)

            service.find(USERNAME)
            repositories.feedIcons.expire(feedId)

            assertEquals(BYTES.toList(), assertNotNull(service.find(USERNAME)).bytes.toList())
            assertEquals(2, engine.requestHistory.size)
        }

    @Test
    fun `アイコンを名乗っていないフィードは取りに行かない`() =
        runTest {
            val repositories = FakeRepositories()
            repositories.addFeed(iconUrl = null)
            val engine = imageEngine()

            assertNull(serviceOf(repositories, engine).find(USERNAME))

            assertEquals(0, engine.requestHistory.size)
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

    /** 置いてあるものの期限を過ぎた時刻に書き換える */
    private fun FakeFeedIconRepository.expire(feedId: FeedId) {
        val stored = assertNotNull(find(feedId))
        save(
            feedId = feedId,
            icon = FeedIcon(
                sourceUrl = stored.sourceUrl,
                contentType = stored.contentType,
                path = stored.path,
                fetchedAt = stored.fetchedAt,
                expiresAt = Instant.now().minusSeconds(1),
            ),
        )
    }

    private fun imageEngine(): MockEngine = MockEngine {
        respond(content = BYTES.decodeToString(), headers = headersOf("Content-Type", "image/png"))
    }

    private fun serviceOf(
        repositories: FakeRepositories,
        engine: MockEngine,
    ): ActorIconService = ActorIconService(
        accounts = repositories.accounts,
        feeds = repositories.feeds,
        icons = repositories.feedIcons,
        store = FeedIconStore(tempDir),
        fetcher = IconFetchService(
            client = HttpClient(engine) { followRedirects = false },
            resolveAddresses = { listOf(InetAddress.getByName("93.184.216.34")) },
        ),
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
