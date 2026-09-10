package net.matsudamper.mastodon.rss.logic

import java.net.InetAddress
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import net.matsudamper.mastodon.rss.FakeRepositories
import net.matsudamper.mastodon.rss.TestImageBytes
import net.matsudamper.mastodon.rss.feed.IconFetchService
import net.matsudamper.mastodon.rss.repository.NewFeed
import net.matsudamper.mastodon.rss.repository.entity.FeedId

// アイコンの中身を取りに行くのはフィードの取り込みのときだけ。ここがその入れ替え。
class FeedIconServiceTest {
    private val tempDir: Path = createTempDirectory("mastodon-rss-feed-icon-test")

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `取り込みのたびに取り直す`() =
        runTest {
            val repositories = FakeRepositories()
            val feedId = repositories.addFeed()
            val engine = imageEngine()
            val service = serviceOf(repositories, engine)

            service.refresh(feedId = feedId, iconUrl = ICON_URL)
            service.refresh(feedId = feedId, iconUrl = ICON_URL)

            assertEquals(2, engine.requestHistory.size)
            assertEquals(ICON_URL, assertNotNull(repositories.feedIcons.find(feedId)).sourceUrl)
        }

    @Test
    fun `Content-Typeに応じた拡張子で保存する`() =
        runTest {
            val cases = listOf(
                Triple("image/png", TestImageBytes.PNG, ".png"),
                Triple("image/jpeg", TestImageBytes.JPEG, ".jpg"),
                Triple("image/gif", TestImageBytes.GIF, ".gif"),
                Triple("image/webp", TestImageBytes.WEBP, ".webp"),
            )

            cases.forEach { (contentType, bytes, extension) ->
                val repositories = FakeRepositories()
                val feedId = repositories.addFeed()
                val engine = MockEngine {
                    respond(
                        content = bytes,
                        headers = headersOf("Content-Type", contentType),
                    )
                }

                serviceOf(repositories, engine).refresh(feedId = feedId, iconUrl = ICON_URL)

                val path = assertNotNull(repositories.feedIcons.find(feedId)).path
                assertTrue(path.endsWith(extension), "保存先: $path")
            }
        }

    @Test
    fun `ICOは埋め込みのPNGに変換して保存する`() =
        runTest {
            val repositories = FakeRepositories()
            val feedId = repositories.addFeed()
            val engine = MockEngine {
                respond(content = TestImageBytes.ICO, headers = headersOf("Content-Type", "image/x-icon"))
            }
            val store = FeedIconStore(tempDir)

            serviceOf(repositories, engine, store).refresh(feedId = feedId, iconUrl = ICON_URL)

            val saved = assertNotNull(repositories.feedIcons.find(feedId))
            assertTrue(saved.path.endsWith(".png"), "保存先: ${saved.path}")
            assertEquals("image/png", saved.contentType)
            assertContentEquals(TestImageBytes.PNG, assertNotNull(store.read(saved.path)))
        }

    @Test
    fun `埋め込みPNGが無いICOは保存しない`() =
        runTest {
            val repositories = FakeRepositories()
            val feedId = repositories.addFeed()
            // ICONDIR + ICONDIRENTRY はあるが、指す先が PNG 署名で始まらない（BMP 埋め込み相当）
            val bmpOnlyIco = byteArrayOf(0, 0, 1, 0, 1, 0) +
                byteArrayOf(32, 32, 0, 0, 1, 0, 32, 0, 4, 0, 0, 0, 22, 0, 0, 0) +
                byteArrayOf(0x28, 0, 0, 0)
            val engine = MockEngine {
                respond(content = bmpOnlyIco, headers = headersOf("Content-Type", "image/x-icon"))
            }

            serviceOf(repositories, engine).refresh(feedId = feedId, iconUrl = ICON_URL)

            assertNull(repositories.feedIcons.find(feedId))
        }

    @Test
    fun `取り直すと前に置いたものは消える`() =
        runTest {
            val repositories = FakeRepositories()
            val feedId = repositories.addFeed()
            val store = FeedIconStore(tempDir)
            val service = serviceOf(repositories, imageEngine(), store)

            service.refresh(feedId = feedId, iconUrl = ICON_URL)
            val first = assertNotNull(repositories.feedIcons.find(feedId)).path

            service.refresh(feedId = feedId, iconUrl = ICON_URL)
            val second = assertNotNull(repositories.feedIcons.find(feedId)).path

            assertNotEquals(first, second)
            assertNull(store.read(first))
            assertNotNull(store.read(second))
        }

    @Test
    fun `名乗らなくなったら置いてあるものを消す`() =
        runTest {
            val repositories = FakeRepositories()
            val feedId = repositories.addFeed()
            val store = FeedIconStore(tempDir)
            val service = serviceOf(repositories, imageEngine(), store)

            service.refresh(feedId = feedId, iconUrl = ICON_URL)
            val path = assertNotNull(repositories.feedIcons.find(feedId)).path

            service.refresh(feedId = feedId, iconUrl = null)

            assertNull(repositories.feedIcons.find(feedId))
            assertNull(store.read(path))
        }

    @Test
    fun `取れなかったときは前のものを残す`() =
        runTest {
            val repositories = FakeRepositories()
            val feedId = repositories.addFeed()
            var served = false
            val engine = MockEngine {
                if (served) {
                    respond(content = "", status = HttpStatusCode.InternalServerError)
                } else {
                    served = true
                    respond(content = BYTES, headers = headersOf("Content-Type", "image/png"))
                }
            }
            val service = serviceOf(repositories, engine)

            service.refresh(feedId = feedId, iconUrl = ICON_URL)
            service.refresh(feedId = feedId, iconUrl = ICON_URL)

            assertEquals(ICON_URL, assertNotNull(repositories.feedIcons.find(feedId)).sourceUrl)
        }

    @Test
    fun `見に来た側に持たせる時間は配信元に従う`() =
        runTest {
            val repositories = FakeRepositories()
            val feedId = repositories.addFeed()
            val engine = MockEngine {
                respond(
                    content = BYTES,
                    headers = headersOf(
                        "Content-Type" to listOf("image/png"),
                        "Cache-Control" to listOf("public, max-age=60"),
                    ),
                )
            }

            val before = Instant.now()
            serviceOf(repositories, engine).refresh(feedId = feedId, iconUrl = ICON_URL)
            val after = Instant.now()

            // 取ってきた時刻は before から after の間なので、期限もその 60 秒後の間に入る
            val expiresAt = assertNotNull(repositories.feedIcons.find(feedId)).expiresAt
            assertTrue(
                expiresAt in before.plusSeconds(60)..after.plusSeconds(60),
                "実際の期限: $expiresAt",
            )
        }

    @Test
    fun `持つなと言われたものはすぐ期限が切れる`() =
        runTest {
            val repositories = FakeRepositories()
            val feedId = repositories.addFeed()
            val engine = MockEngine {
                respond(
                    content = BYTES,
                    headers = headersOf(
                        "Content-Type" to listOf("image/png"),
                        "Cache-Control" to listOf("no-store"),
                    ),
                )
            }

            val before = Instant.now()
            serviceOf(repositories, engine).refresh(feedId = feedId, iconUrl = ICON_URL)

            val stored = assertNotNull(repositories.feedIcons.find(feedId))
            assertTrue(stored.expiresAt <= Instant.now(), "実際の期限: ${stored.expiresAt}")
            assertTrue(stored.expiresAt >= before, "実際の期限: ${stored.expiresAt}")
        }

    private fun FakeRepositories.addFeed(): FeedId {
        val account = assertNotNull(accounts.add(username = USERNAME, createdAt = Instant.now()))
        val feed = assertNotNull(
            feeds.add(
                NewFeed(
                    accountId = account.id,
                    url = FEED_URL,
                    title = "サンプル",
                    siteUrl = SITE_URL,
                    format = "RSS 2.0",
                    iconUrl = ICON_URL,
                    pollIntervalSeconds = 900,
                ),
            ),
        )
        return feed.id
    }

    private fun imageEngine(): MockEngine = MockEngine {
        respond(content = BYTES, headers = headersOf("Content-Type", "image/png"))
    }

    private fun serviceOf(
        repositories: FakeRepositories,
        engine: MockEngine,
        store: FeedIconStore = FeedIconStore(tempDir),
    ): FeedIconService = FeedIconService(
        icons = repositories.feedIcons,
        store = store,
        fetcher = IconFetchService(
            client = HttpClient(engine) { followRedirects = false },
            resolveAddresses = { listOf(InetAddress.getByName("93.184.216.34")) },
        ),
        defaultFreshFor = Duration.ofDays(1),
    )

    private companion object {
        const val USERNAME = "feed1"
        const val FEED_URL = "https://example.com/feed.xml"
        const val SITE_URL = "https://example.com/"
        const val ICON_URL = "https://example.com/icon.png"
        val BYTES: ByteArray = TestImageBytes.PNG
    }
}
