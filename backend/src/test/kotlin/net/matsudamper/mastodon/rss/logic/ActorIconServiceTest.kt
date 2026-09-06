package net.matsudamper.mastodon.rss.logic

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.headersOf
import net.matsudamper.mastodon.rss.actor.FeedLinks
import net.matsudamper.mastodon.rss.actor.StoredFeedLinks
import net.matsudamper.mastodon.rss.feed.IconFetchService

// /users/{name}/icon は無認証で誰でも叩ける。叩かれた数だけ配信元へ出ていかないことを見る。
class ActorIconServiceTest {
    @Test
    fun `2 回目は配信元に取りに行かない`() =
        runTest {
            val engine = pngEngine()
            val service = ActorIconService(
                feedLinks = MutableFeedLinks(feedLinks("https://example.com/icon.png")),
                icons = iconsOf(engine),
            )

            assertNotNull(service.find(USERNAME))
            assertNotNull(service.find(USERNAME))

            assertEquals(1, engine.requestHistory.size)
        }

    @Test
    fun `取得元が変わったら取り直す`() =
        runTest {
            val engine = pngEngine()
            val links = MutableFeedLinks(feedLinks("https://example.com/icon.png"))
            val service = ActorIconService(feedLinks = links, icons = iconsOf(engine))

            assertNotNull(service.find(USERNAME))
            links.value = feedLinks("https://example.com/icon2.png")
            assertNotNull(service.find(USERNAME))

            assertEquals(
                listOf("/icon.png", "/icon2.png"),
                engine.requestHistory.map { it.url.encodedPath },
            )
        }

    @Test
    fun `アイコンを持たないアカウントは取りに行かない`() =
        runTest {
            val engine = pngEngine()
            val service = ActorIconService(
                feedLinks = MutableFeedLinks(feedLinks(null)),
                icons = iconsOf(engine),
            )

            assertNull(service.find(USERNAME))

            assertEquals(0, engine.requestHistory.size)
        }

    private fun pngEngine(): MockEngine = MockEngine {
        respond(content = "PNG", headers = headersOf("Content-Type", "image/png"))
    }

    private fun iconsOf(engine: MockEngine): IconFetchService = IconFetchService(
        client = HttpClient(engine) { followRedirects = false },
        resolveAddresses = { listOf(InetAddress.getByName("93.184.216.34")) },
    )

    private fun feedLinks(iconUrl: String?): FeedLinks = FeedLinks(
        siteUrl = "https://example.com/",
        feedUrl = "https://example.com/feed.xml",
        iconUrl = iconUrl,
    )

    /** 途中で取得元が変わる場合を作るための引き先 */
    private class MutableFeedLinks(
        var value: FeedLinks,
    ) : StoredFeedLinks {
        override fun find(username: String): FeedLinks = value
    }

    private companion object {
        const val USERNAME = "feed1"
    }
}
