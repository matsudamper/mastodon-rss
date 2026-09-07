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
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
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
                    respond(content = "JPEG", headers = headersOf("Content-Type", "image/jpeg"))
                },
            )

            service.refresh(FEED_ID, HEADER_URL)

            assertEquals(HEADER_URL, assertNotNull(repository.find(FEED_ID)).sourceUrl)
        }

    @Test
    fun `取得に失敗したら前のヘッダーを残す`() =
        runTest {
            val repository = MemoryFeedHeaderRepository()
            val success = serviceOf(
                repository = repository,
                engine = MockEngine {
                    respond(content = "JPEG", headers = headersOf("Content-Type", "image/jpeg"))
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
