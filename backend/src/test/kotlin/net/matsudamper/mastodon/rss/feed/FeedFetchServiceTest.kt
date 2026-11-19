package net.matsudamper.mastodon.rss.feed

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.test.runTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.respondRedirect
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

// YouTube の /@handle はチャンネルのページを引かないとフィードの URL が決まらない。
// このページで起きた失敗が URL の不正に潰れないことを確認する。
// 期待値のページの形は実際に YouTube が返したものに合わせてある。
class FeedFetchServiceTest {
    private val channelId = "UCHVXbQzkl3rDfsXWo8xi2qw"
    private val handleUrl = "https://www.youtube.com/@AngeKatrina"
    private val feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"

    @Test
    fun `チャンネルのページが 2MiB を超えても登録できる`() =
        runTest {
            val engine = MockEngine { request ->
                if (request.url.encodedPath.endsWith("videos.xml")) {
                    respondXml(atomFeed)
                } else {
                    respondHtml(channelPageHtml(padding = 2 * 1024 * 1024))
                }
            }

            val result = serviceOf(engine).fetch(handleUrl, needsDescription = true)

            assertEquals(feedUrl, assertIs<FeedFetchService.FetchResult.Success>(result).feedUrl)
        }

    @Test
    fun `チャンネルのページを取れなかったら取得の失敗として返す`() =
        runTest {
            val engine = MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }

            val result = serviceOf(engine).fetch(handleUrl, needsDescription = true)

            assertEquals(
                HttpStatusCode.ServiceUnavailable.value,
                assertIs<FeedFetchService.FetchResult.HttpError>(result).status,
            )
        }

    @Test
    fun `チャンネル ID を名乗らないページは URL の不正にしない`() =
        runTest {
            val engine = MockEngine { respondHtml("<html><head><title>同意</title></head></html>") }

            val result = serviceOf(engine).fetch(handleUrl, needsDescription = true)

            assertEquals(FeedFetchService.FetchResult.ChannelIdNotFound, result)
        }

    @Test
    fun `内側のアドレスへのリダイレクトは辿らない`() =
        runTest {
            val requestedHosts = mutableListOf<String>()
            val engine = MockEngine { request ->
                requestedHosts += request.url.host
                if (request.url.host == "example.com") {
                    respondRedirect("http://internal.example/feed.xml")
                } else {
                    respondXml(atomFeed)
                }
            }
            val service = FeedFetchService(
                client = HttpClient(engine) { followRedirects = false },
                resolveAddresses = { host ->
                    listOf(InetAddress.getByName(if (host == "example.com") "93.184.216.34" else "10.0.0.1"))
                },
            )

            val result = service.fetch("https://example.com/feed.xml", needsDescription = false)

            assertIs<FeedFetchService.FetchResult.HttpError>(result)
            assertEquals(listOf("example.com"), requestedHosts)
        }

    @Test
    fun `内側のアドレスを指す URL は取りに行かない`() =
        runTest {
            var requested = false
            val engine = MockEngine {
                requested = true
                respondXml(atomFeed)
            }
            val service = FeedFetchService(
                client = HttpClient(engine) { followRedirects = false },
                resolveAddresses = { listOf(InetAddress.getByName("127.0.0.1")) },
            )

            val result = service.fetch("http://localhost/feed.xml", needsDescription = false)

            assertIs<FeedFetchService.FetchResult.HttpError>(result)
            assertEquals(false, requested)
        }

    @Test
    fun `外のアドレスへのリダイレクトは辿って飛んだ先の URL を返す`() =
        runTest {
            val engine = MockEngine { request ->
                if (request.url.host == "example.com") {
                    respondRedirect("https://cdn.example.net/feed.xml")
                } else {
                    respondXml(atomFeed)
                }
            }

            val result = serviceOf(engine).fetch("https://example.com/feed.xml", needsDescription = false)

            assertEquals(
                "https://cdn.example.net/feed.xml",
                assertIs<FeedFetchService.FetchResult.Success>(result).feedUrl,
            )
        }

    @Test
    fun `クエリだけのリダイレクトでは元のパスを保つ`() =
        runTest {
            val engine = MockEngine { request ->
                if (request.url.parameters["token"] == null) {
                    respondRedirect("?token=new")
                } else {
                    respondXml(atomFeed)
                }
            }

            val result = serviceOf(engine).fetch("https://example.com/rss/feed.xml?old=1", needsDescription = false)

            assertEquals(
                "https://example.com/rss/feed.xml?token=new",
                assertIs<FeedFetchService.FetchResult.Success>(result).feedUrl,
            )
        }

    @Test
    fun `名前を引くのが終わらなければ待ちきらずに取得の失敗として返す`() =
        runTest {
            var requested = false
            val engine = MockEngine {
                requested = true
                respondXml(atomFeed)
            }
            val service = FeedFetchService(
                client = HttpClient(engine) { followRedirects = false },
                resolveAddresses = {
                    Thread.sleep(5_000)
                    listOf(InetAddress.getByName("93.184.216.34"))
                },
                resolveTimeout = 100.milliseconds,
            )

            val result = service.fetch("https://example.com/feed.xml", needsDescription = false)

            assertIs<FeedFetchService.FetchResult.HttpError>(result)
            assertEquals(false, requested)
            service.close()
        }

    private fun serviceOf(engine: MockEngine): FeedFetchService = FeedFetchService(
        client = HttpClient(engine) { followRedirects = false },
        resolveAddresses = { listOf(InetAddress.getByName("93.184.216.34")) },
    )

    private fun MockRequestHandleScope.respondHtml(html: String) =
        respond(content = html, headers = headersOf("Content-Type", "text/html; charset=utf-8"))

    private fun MockRequestHandleScope.respondXml(xml: String) =
        respond(content = xml, headers = headersOf("Content-Type", "application/atom+xml"))

    // ページの大きさだけを変えたいので、名乗りの後ろに詰め物を置く
    private fun channelPageHtml(padding: Int): String =
        """
        <html><head>
        <link rel="canonical" href="https://www.youtube.com/channel/$channelId">
        </head><body>${"a".repeat(padding)}</body></html>
        """.trimIndent()

    private val atomFeed =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <title>Ange Katrina</title>
          <link rel="alternate" href="https://www.youtube.com/channel/$channelId"/>
          <entry>
            <title>動画</title>
            <link rel="alternate" href="https://www.youtube.com/watch?v=00000000000"/>
            <id>yt:video:00000000000</id>
            <published>2026-09-01T00:00:00Z</published>
          </entry>
        </feed>
        """.trimIndent()
}
