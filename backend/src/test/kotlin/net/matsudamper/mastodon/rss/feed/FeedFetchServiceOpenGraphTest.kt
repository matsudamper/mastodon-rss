package net.matsudamper.mastodon.rss.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

// 記事のリンク先を取りに行くところ。内部ネットワークを断るのと、飛ばされた先も
// 同じように見るのがここの仕事。抜けても画像が出なくなるだけで気付けないので、
// 断れていることをテストで固定する。
class FeedFetchServiceOpenGraphTest {
    @Test
    fun `og image を返す`() =
        runTest {
            val requested = mutableListOf<String>()
            val service = serviceOf(requested) { html("https://example.com/ogp.png") }

            assertEquals("https://example.com/ogp.png", service.fetchOpenGraphImageUrl("https://example.com/1"))
            assertEquals(listOf("https://example.com/1"), requested)
        }

    @Test
    fun `内部を指すリンクは取りに行かない`() =
        runTest {
            val requested = mutableListOf<String>()
            val service = serviceOf(requested, externalHosts = allowing(false)) { html("https://example.com/ogp.png") }

            assertNull(service.fetchOpenGraphImageUrl("http://169.254.169.254/latest/meta-data/"))
            assertEquals(listOf<String>(), requested)
        }

    @Test
    fun `飛ばされた先が内部なら取りに行かない`() =
        runTest {
            val requested = mutableListOf<String>()
            // 外から内部へ飛ばす。1 回目だけ通して 2 回目を断る
            val service = serviceOf(requested, externalHosts = allowing(true, false)) { redirect("http://127.0.0.1/") }

            assertNull(service.fetchOpenGraphImageUrl("https://example.com/1"))
            assertEquals(listOf("https://example.com/1"), requested)
        }

    @Test
    fun `飛ばされた先の og image を返す`() =
        runTest {
            val requested = mutableListOf<String>()
            val service = serviceOf(requested) { request ->
                if (request.url.encodedPath == "/1") {
                    redirect("https://example.net/moved")
                } else {
                    html("/ogp.png")
                }
            }

            // 相対 URL の基準は飛んだ先のページ
            assertEquals("https://example.net/ogp.png", service.fetchOpenGraphImageUrl("https://example.com/1"))
            assertEquals(listOf("https://example.com/1", "https://example.net/moved"), requested)
        }

    @Test
    fun `飛ばされ続けたら諦める`() =
        runTest {
            val requested = mutableListOf<String>()
            var next = 0
            val service = serviceOf(requested) { redirect("https://example.com/${next++}") }

            assertNull(service.fetchOpenGraphImageUrl("https://example.com/start"))
            // 際限なく辿らない
            assertEquals(4, requested.size)
        }

    @Test
    fun `URL として長すぎる og image は持たない`() =
        runTest {
            val requested = mutableListOf<String>()
            // content には長さの上限が無い。保存も配信もこの値を毎回載せることになる
            val service = serviceOf(requested) { html("https://example.com/" + "a".repeat(3000) + ".png") }

            assertNull(service.fetchOpenGraphImageUrl("https://example.com/1"))
        }

    @Test
    fun `内部を指す og image は持たない`() =
        runTest {
            val requested = mutableListOf<String>()
            // ページは外、画像は内部。添付は連合先が取りに行くので、渡してはいけない
            val service = serviceOf(requested, externalHosts = allowing(true, false)) {
                html("http://127.0.0.1/ogp.png")
            }

            assertNull(service.fetchOpenGraphImageUrl("https://example.com/1"))
        }

    @Test
    fun `HTML でなければ読まない`() =
        runTest {
            val requested = mutableListOf<String>()
            val service = serviceOf(requested) {
                respond(
                    content = """<meta property="og:image" content="https://example.com/ogp.png">""",
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type", "application/pdf"),
                )
            }

            assertNull(service.fetchOpenGraphImageUrl("https://example.com/1.pdf"))
        }

    @Test
    fun `取得に失敗しても例外にしない`() =
        runTest {
            val requested = mutableListOf<String>()
            val service = serviceOf(requested) { respond(content = "", status = HttpStatusCode.NotFound) }

            assertNull(service.fetchOpenGraphImageUrl("https://example.com/1"))
        }

    @Test
    fun `http でも https でもない URL は取りに行かない`() =
        runTest {
            val requested = mutableListOf<String>()
            val service = serviceOf(requested) { html("https://example.com/ogp.png") }

            assertNull(service.fetchOpenGraphImageUrl("file:///etc/passwd"))
            assertEquals(listOf<String>(), requested)
        }

    private fun MockRequestHandleScope.html(imageUrl: String): HttpResponseData =
        respond(
            content = """<html><head><meta property="og:image" content="$imageUrl"></head></html>""",
            status = HttpStatusCode.OK,
            headers = headersOf("Content-Type", "text/html; charset=utf-8"),
        )

    private fun MockRequestHandleScope.redirect(location: String): HttpResponseData =
        respond(
            content = "",
            status = HttpStatusCode.Found,
            headers = headersOf("Location", location),
        )

    /**
     * 前から順に返す。尽きたら断る
     */
    private fun allowing(vararg results: Boolean): ExternalHosts = object : ExternalHosts {
        private val answers = ArrayDeque(results.toList())

        override suspend fun isExternal(url: String): Boolean = answers.removeFirstOrNull() ?: false
    }

    private fun serviceOf(
        requested: MutableList<String>,
        externalHosts: ExternalHosts = TestExternalHosts,
        handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): FeedFetchService {
        val engine = MockEngine { request ->
            requested += request.url.toString()
            handler(this, request)
        }
        return FeedFetchService(HttpClient(engine), externalHosts = externalHosts)
    }
}
