package net.matsudamper.mastodon.rss.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
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
    fun `フィードにアイコンが無ければ Web ページの favicon を使う`() =
        runTest {
            val engine = MockEngine { request ->
                when (request.url.encodedPath) {
                    "/feed.xml" -> respondXml(rssFeedWithoutIcon)
                    "/blog/" -> respondHtml("""<html><head><link rel="icon" href="/assets/favicon.png"></head></html>""")
                    else -> error("unexpected request: ${request.url}")
                }
            }

            val result = serviceOf(engine).fetch("https://example.com/feed.xml", needsDescription = false)

            assertEquals(
                "https://example.com/assets/favicon.png",
                assertIs<FeedFetchService.FetchResult.Success>(result).parsed.iconUrl,
            )
        }

    @Test
    fun `フィードがアイコンを名乗っていれば favicon は見に行かない`() =
        runTest {
            val engine = MockEngine { request ->
                when (request.url.encodedPath) {
                    "/feed.xml" -> respondXml(rssFeedWithIcon)
                    else -> error("unexpected request: ${request.url}")
                }
            }

            val result = serviceOf(engine).fetch("https://example.com/feed.xml", needsDescription = false)

            assertEquals(
                "https://cdn.example.com/feed-icon.png",
                assertIs<FeedFetchService.FetchResult.Success>(result).parsed.iconUrl,
            )
        }

    private fun serviceOf(engine: MockEngine): FeedFetchService =
        FeedFetchService(
            client = HttpClient(engine),
            enableFaviconLookup = true,
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

    private val rssFeedWithoutIcon =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
          <channel>
            <title>Example</title>
            <link>https://example.com/blog/</link>
          </channel>
        </rss>
        """.trimIndent()

    private val rssFeedWithIcon =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:webfeeds="http://webfeeds.org/rss/1.0">
          <channel>
            <title>Example</title>
            <link>https://example.com/blog/</link>
            <webfeeds:icon>https://cdn.example.com/feed-icon.png</webfeeds:icon>
          </channel>
        </rss>
        """.trimIndent()
}
