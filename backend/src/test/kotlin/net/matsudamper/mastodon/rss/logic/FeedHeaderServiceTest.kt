package net.matsudamper.mastodon.rss.logic

import java.net.InetAddress
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import net.matsudamper.mastodon.rss.TestImageBytes
import net.matsudamper.mastodon.rss.feed.IconFetchService
import net.matsudamper.mastodon.rss.repository.FeedHeader
import net.matsudamper.mastodon.rss.repository.FeedHeaderRepository
import net.matsudamper.mastodon.rss.repository.entity.FeedId

class FeedHeaderServiceTest {
    private val tempDir: Path = createTempDirectory("mastodon-rss-feed-header-test")

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `取得したヘッダーを保存する`() =
        runTest {
            val repository = MemoryFeedHeaderRepository()
            val service = serviceOf(
                repository = repository,
                engine = MockEngine {
                    respond(content = TestImageBytes.JPEG, headers = headersOf("Content-Type", "image/jpeg"))
                },
            )

            service.refresh(FEED_ID, HEADER_URL)

            assertEquals(HEADER_URL, assertNotNull(repository.find(FEED_ID)).sourceUrl)
        }

    @Test
    fun `同じURLの画像内容が変わると版も変わる`() =
        runTest {
            assertEquals("FB".hashCode(), "Ea".hashCode())
            var content = TestImageBytes.jpegOf("FB")
            val repository = MemoryFeedHeaderRepository()
            val service = serviceOf(
                repository = repository,
                // 期限内は取り直さないので、取り直す状態にしてから中身を変える
                engine = MockEngine {
                    respond(
                        content = content,
                        headers = headersOf(
                            "Content-Type" to listOf("image/jpeg"),
                            "Cache-Control" to listOf("max-age=0"),
                        ),
                    )
                },
            )

            service.refresh(FEED_ID, HEADER_URL)
            val first = assertNotNull(repository.find(FEED_ID))

            content = TestImageBytes.jpegOf("Ea")
            service.refresh(FEED_ID, HEADER_URL)
            val second = assertNotNull(repository.find(FEED_ID))

            assertEquals(first.sourceUrl, second.sourceUrl)
            assertNotEquals(first.revision, second.revision)
        }

    @Test
    fun `ICOはヘッダーとして保存しない`() =
        runTest {
            val repository = MemoryFeedHeaderRepository()
            val service = serviceOf(
                repository = repository,
                engine = MockEngine {
                    respond(content = TestImageBytes.ICO, headers = headersOf("Content-Type", "image/x-icon"))
                },
            )

            service.refresh(FEED_ID, HEADER_URL)

            assertNull(repository.find(FEED_ID))
        }

    @Test
    fun `期限内の同じURLは取り直さない`() =
        runTest {
            val repository = MemoryFeedHeaderRepository()
            var requestCount = 0
            val service = serviceOf(
                repository = repository,
                engine = MockEngine {
                    requestCount += 1
                    respond(content = TestImageBytes.JPEG, headers = headersOf("Content-Type", "image/jpeg"))
                },
            )

            service.refresh(FEED_ID, HEADER_URL)
            service.refresh(FEED_ID, HEADER_URL)

            assertEquals(1, requestCount)
        }

    @Test
    fun `期限が切れていたら取り直す`() =
        runTest {
            val repository = MemoryFeedHeaderRepository()
            var requestCount = 0
            val service = serviceOf(
                repository = repository,
                engine = MockEngine {
                    requestCount += 1
                    respond(
                        content = TestImageBytes.JPEG,
                        headers = headersOf(
                            "Content-Type" to listOf("image/jpeg"),
                            "Cache-Control" to listOf("max-age=0"),
                        ),
                    )
                },
            )

            service.refresh(FEED_ID, HEADER_URL)
            service.refresh(FEED_ID, HEADER_URL)

            assertEquals(2, requestCount)
        }

    @Test
    fun `取得に失敗したら前のヘッダーを残す`() =
        runTest {
            val repository = MemoryFeedHeaderRepository()
            val success = serviceOf(
                repository = repository,
                engine = MockEngine {
                    respond(content = TestImageBytes.JPEG, headers = headersOf("Content-Type", "image/jpeg"))
                },
            )
            success.refresh(FEED_ID, HEADER_URL)

            val failure = serviceOf(
                repository = repository,
                engine = MockEngine {
                    respond(content = "", status = HttpStatusCode.InternalServerError)
                },
            )
            failure.refresh(FEED_ID, NEW_HEADER_URL)

            assertEquals(HEADER_URL, assertNotNull(repository.find(FEED_ID)).sourceUrl)
        }

    private fun serviceOf(
        repository: FeedHeaderRepository,
        engine: MockEngine,
    ): FeedHeaderService = FeedHeaderService(
        headers = repository,
        store = FeedIconStore(tempDir),
        fetcher = IconFetchService(
            client = HttpClient(engine) { followRedirects = false },
            resolveAddresses = { listOf(InetAddress.getByName("93.184.216.34")) },
        ),
        defaultFreshFor = Duration.ofDays(1),
    )

    private class MemoryFeedHeaderRepository : FeedHeaderRepository {
        private val values = mutableMapOf<FeedId, FeedHeader>()

        override fun find(feedId: FeedId): FeedHeader? = values[feedId]

        override fun save(
            feedId: FeedId,
            header: FeedHeader,
        ) {
            values[feedId] = header
        }

        override fun delete(feedId: FeedId) {
            values.remove(feedId)
        }
    }

    private companion object {
        val FEED_ID = FeedId(1)
        const val HEADER_URL = "https://example.com/header.jpg"
        const val NEW_HEADER_URL = "https://example.com/new-header.jpg"
    }
}
