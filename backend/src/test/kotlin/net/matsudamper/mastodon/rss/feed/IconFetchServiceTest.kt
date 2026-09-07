package net.matsudamper.mastodon.rss.feed

import java.net.InetAddress
import java.net.UnknownHostException
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

// アイコンの取得元はフィードに配信元が書いた URL で、こちらでは選べない。
// 取得は無認証のエンドポイントから呼ばれるので、何を取りに行って何を返すかを固定する。
class IconFetchServiceTest {
    @Test
    fun `画像として復号できる種類は取得元の種類のまま返す`() =
        runTest {
            val engine = MockEngine {
                respond(
                    content = "PNG",
                    headers = headersOf("Content-Type", ContentType.Image.PNG.toString()),
                )
            }

            val result = serviceOf(engine).fetch("https://example.com/icon.png")

            assertEquals(ContentType.Image.PNG, assertIs<IconFetchService.FetchResult.Success>(result).contentType)
        }

    @Test
    fun `SVG は返さない`() =
        runTest {
            val engine = MockEngine {
                respond(
                    content = """<svg xmlns="http://www.w3.org/2000/svg"><script/></svg>""",
                    headers = headersOf("Content-Type", "image/svg+xml"),
                )
            }

            val result = serviceOf(engine).fetch("https://example.com/icon.svg")

            assertEquals(IconFetchService.FetchResult.Failure, result)
        }

    @Test
    fun `内側を指す URL には取りに行かない`() =
        runTest {
            val engine = pngEngine()

            val result = serviceOf(engine, address = "127.0.0.1").fetch("http://localhost:8080/icon.png")

            assertEquals(IconFetchService.FetchResult.Failure, result)
            assertEquals(0, engine.requestHistory.size)
        }

    @Test
    fun `名前を引くのに時間がかかる URL は取りに行かない`() =
        runTest {
            val engine = pngEngine()

            val service = IconFetchService(
                client = HttpClient(engine) { followRedirects = false },
                resolveAddresses = {
                    Thread.sleep(2_000)
                    listOf(InetAddress.getByName("93.184.216.34"))
                },
                resolveTimeout = Duration.ofMillis(50),
            )

            val startedAt = System.nanoTime()
            val result = service.fetch("https://example.com/icon.png")
            val elapsed = Duration.ofNanos(System.nanoTime() - startedAt)

            assertIs<IconFetchService.FetchResult.Failure>(result)
            assertEquals(0, engine.requestHistory.size)
            // 引き終わるのを待たずに戻る。待っていると取り込み全体が止まる
            assertTrue(elapsed < Duration.ofSeconds(1), "実際に待った時間: $elapsed")
        }

    @Test
    fun `名前を引けない URL には取りに行かない`() =
        runTest {
            val engine = pngEngine()
            val service = IconFetchService(
                client = HttpClient(engine) { followRedirects = false },
                resolveAddresses = { throw UnknownHostException(it) },
            )

            val result = service.fetch("https://example.com/icon.png")

            assertEquals(IconFetchService.FetchResult.Failure, result)
            assertEquals(0, engine.requestHistory.size)
        }

    @Test
    fun `リダイレクトの飛び先が内側なら取りに行かない`() =
        runTest {
            val engine = redirectingEngine()
            val service = IconFetchService(
                client = HttpClient(engine) { followRedirects = false },
                resolveAddresses = { host ->
                    listOf(InetAddress.getByName(if (host == "example.com") PUBLIC_ADDRESS else "10.0.0.1"))
                },
            )

            val result = service.fetch("https://example.com/icon.png")

            assertEquals(IconFetchService.FetchResult.Failure, result)
            assertEquals(listOf("example.com"), engine.requestHistory.map { it.url.host })
        }

    @Test
    fun `リダイレクトは自分で辿る`() =
        runTest {
            val engine = redirectingEngine()

            val result = serviceOf(engine).fetch("https://example.com/icon.png")

            assertIs<IconFetchService.FetchResult.Success>(result)
            assertEquals(
                listOf("example.com", "cdn.example.net"),
                engine.requestHistory.map { it.url.host },
            )
        }

    @Test
    fun `配信元が言う max-age を返す`() =
        runTest {
            val engine = MockEngine {
                respond(
                    content = "PNG",
                    headers = headersOf(
                        "Content-Type" to listOf("image/png"),
                        "Cache-Control" to listOf("public, max-age=600"),
                    ),
                )
            }

            val result = serviceOf(engine).fetch("https://example.com/icon.png")

            assertEquals(Duration.ofSeconds(600), assertIs<IconFetchService.FetchResult.Success>(result).freshFor)
        }

    @Test
    fun `長すぎる max-age は 1 日で切る`() =
        runTest {
            val engine = MockEngine {
                respond(
                    content = "PNG",
                    headers = headersOf(
                        "Content-Type" to listOf("image/png"),
                        // そのまま足すと期限の計算が溢れる大きさ
                        "Cache-Control" to listOf("public, max-age=9000000000000000000"),
                    ),
                )
            }

            val result = serviceOf(engine).fetch("https://example.com/icon.png")

            assertEquals(Duration.ofDays(1), assertIs<IconFetchService.FetchResult.Success>(result).freshFor)
        }

    @Test
    fun `共有キャッシュ向けの s-maxage は max-age として読まない`() =
        runTest {
            val engine = MockEngine {
                respond(
                    content = "PNG",
                    headers = headersOf(
                        "Content-Type" to listOf("image/png"),
                        "Cache-Control" to listOf("public, s-maxage=86400"),
                    ),
                )
            }

            val result = serviceOf(engine).fetch("https://example.com/icon.png")

            assertNull(assertIs<IconFetchService.FetchResult.Success>(result).freshFor)
        }

    @Test
    fun `no-store のときは持ち越さない`() =
        runTest {
            val engine = MockEngine {
                respond(
                    content = "PNG",
                    headers = headersOf(
                        "Content-Type" to listOf("image/png"),
                        "Cache-Control" to listOf("no-store"),
                    ),
                )
            }

            val result = serviceOf(engine).fetch("https://example.com/icon.png")

            assertEquals(Duration.ZERO, assertIs<IconFetchService.FetchResult.Success>(result).freshFor)
        }

    private fun pngEngine(): MockEngine = MockEngine {
        respond(content = "PNG", headers = headersOf("Content-Type", "image/png"))
    }

    private fun redirectingEngine(): MockEngine = MockEngine { request ->
        if (request.url.host == "example.com") {
            respond(
                content = "",
                status = HttpStatusCode.Found,
                headers = headersOf("Location", "https://cdn.example.net/icon.png"),
            )
        } else {
            respond(content = "PNG", headers = headersOf("Content-Type", "image/png"))
        }
    }

    private fun serviceOf(
        engine: MockEngine,
        address: String = PUBLIC_ADDRESS,
    ): IconFetchService = IconFetchService(
        client = HttpClient(engine) { followRedirects = false },
        resolveAddresses = { listOf(InetAddress.getByName(address)) },
    )

    private companion object {
        const val PUBLIC_ADDRESS = "93.184.216.34"
    }
}
