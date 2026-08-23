package net.matsudamper.mastodon.rss.logic

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import net.matsudamper.mastodon.rss.FakeRepositories
import net.matsudamper.mastodon.rss.feed.FeedFetchService
import net.matsudamper.mastodon.rss.repository.AccountId

class FeedServiceTest {
    @Test
    fun `取得できたフィードを保存する`() =
        runTest {
            val repositories = FakeRepositories()
            val account = assertNotNull(repositories.accounts.add(username = "feed1", createdAt = CREATED_AT))
            val service = serviceOf(repositories)

            val result = service.save(accountId = account.id, url = FEED_URL)

            val success = assertIs<FeedService.SaveResult.Success>(result)
            assertEquals(FEED_URL, success.feed.url)
            assertEquals("サンプル", success.feed.title)
            assertEquals("RSS 2.0", success.feed.format)
            assertEquals(success.feed, repositories.feeds.findByAccountId(account.id))
        }

    @Test
    fun `同じアカウントに 2 本目は登録できない`() =
        runTest {
            val repositories = FakeRepositories()
            val account = assertNotNull(repositories.accounts.add(username = "feed1", createdAt = CREATED_AT))
            val service = serviceOf(repositories)
            service.save(accountId = account.id, url = FEED_URL)

            val result = service.save(accountId = account.id, url = "https://example.com/other.xml")

            val failure = assertIs<FeedService.SaveResult.Failure>(result)
            assertEquals(FeedService.SaveFailure.ALREADY_HAS_FEED, failure.reason)
        }

    @Test
    fun `同じ URL は別のアカウントにも登録できない`() =
        runTest {
            val repositories = FakeRepositories()
            val account1 = assertNotNull(repositories.accounts.add(username = "feed1", createdAt = CREATED_AT))
            val account2 = assertNotNull(repositories.accounts.add(username = "feed2", createdAt = CREATED_AT))
            val service = serviceOf(repositories)
            service.save(accountId = account1.id, url = FEED_URL)

            val result = service.save(accountId = account2.id, url = FEED_URL)

            val failure = assertIs<FeedService.SaveResult.Failure>(result)
            assertEquals(FeedService.SaveFailure.DUPLICATE_URL, failure.reason)
        }

    @Test
    fun `知らないアカウントには登録できない`() =
        runTest {
            val repositories = FakeRepositories()
            val service = serviceOf(repositories)

            val result = service.save(accountId = AccountId(999), url = FEED_URL)

            val failure = assertIs<FeedService.SaveResult.Failure>(result)
            assertEquals(FeedService.SaveFailure.UNKNOWN_ACCOUNT, failure.reason)
        }

    @Test
    fun `取れなければ保存しない`() =
        runTest {
            val repositories = FakeRepositories()
            val account = assertNotNull(repositories.accounts.add(username = "feed1", createdAt = CREATED_AT))
            val service = serviceOf(repositories, status = HttpStatusCode.NotFound)

            val result = service.save(accountId = account.id, url = FEED_URL)

            val failure = assertIs<FeedService.SaveResult.Failure>(result)
            assertEquals(FeedService.SaveFailure.FETCH_FAILED, failure.reason)
            assertEquals(null, repositories.feeds.findByAccountId(account.id))
        }

    @Test
    fun `プレビューは記事の数と見本を返す`() =
        runTest {
            val service = serviceOf(FakeRepositories())

            val result = service.preview(FEED_URL)

            val success = assertIs<FeedService.PreviewResult.Success>(result)
            assertEquals("サンプル", success.preview.title)
            assertEquals(2, success.preview.itemCount)
            assertEquals(listOf("1 本目", "2 本目"), success.preview.sampleItems.map { it.title })
        }

    private fun serviceOf(
        repositories: FakeRepositories,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): FeedService {
        val engine = MockEngine {
            respond(
                content = if (status == HttpStatusCode.OK) FEED_XML else "",
                status = status,
                headers = headersOf("Content-Type", "application/rss+xml"),
            )
        }

        return FeedService(
            accounts = repositories.accounts,
            feeds = repositories.feeds,
            fetcher = FeedFetchService(HttpClient(engine)),
        )
    }

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-08-16T01:02:03Z")
        const val FEED_URL = "https://example.com/feed.xml"
        val FEED_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>サンプル</title>
                <link>https://example.com/</link>
                <item><title>1 本目</title><link>https://example.com/1</link></item>
                <item><title>2 本目</title><link>https://example.com/2</link></item>
              </channel>
            </rss>
        """.trimIndent()
    }
}
