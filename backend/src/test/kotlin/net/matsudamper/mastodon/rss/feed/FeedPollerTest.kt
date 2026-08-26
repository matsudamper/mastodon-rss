package net.matsudamper.mastodon.rss.feed

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import net.matsudamper.mastodon.rss.FakeFollowerStore
import net.matsudamper.mastodon.rss.FakeNoteStore
import net.matsudamper.mastodon.rss.FakeRepositories
import net.matsudamper.mastodon.rss.TestDelivery
import net.matsudamper.mastodon.rss.TestLocalActor
import net.matsudamper.mastodon.rss.logic.FeedService
import net.matsudamper.mastodon.rss.note.NotePublisher
import net.matsudamper.mastodon.rss.repository.Feed
import net.matsudamper.mastodon.rss.repository.FeedRepository

class FeedPollerTest {
    @Test
    fun `1 周目が失敗しても次の周で新着を投稿する`() =
        runBlocking {
            val repositories = FakeRepositories()
            val noteStore = FakeNoteStore()
            val account = assertNotNull(repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT))
            val feeds = FailingOnceFeedRepository(repositories.feeds)
            val service = serviceOf(repositories = repositories, feeds = feeds, noteStore = noteStore)
            service.save(accountId = account.id, url = FEED_URL)

            val job = FeedPoller(feedService = service, checkInterval = CHECK_INTERVAL).start(this)
            withTimeout(TIMEOUT) {
                while (noteStore.added.isEmpty()) {
                    delay(CHECK_INTERVAL)
                }
            }
            job.cancel()

            assertEquals(
                listOf("""<p>1 本目<br><a href="https://example.com/1">https://example.com/1</a></p>"""),
                noteStore.added.map { it.contentHtml },
            )
        }

    private fun serviceOf(
        repositories: FakeRepositories,
        feeds: FeedRepository,
        noteStore: FakeNoteStore,
    ): FeedService {
        val engine = MockEngine {
            respond(
                content = FEED_XML,
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/rss+xml"),
            )
        }
        return FeedService(
            accounts = repositories.accounts,
            feeds = feeds,
            feedItems = repositories.feedItems,
            fetcher = FeedFetchService(HttpClient(engine)),
            actorDirectory = TestLocalActor.directory,
            notePublisher = NotePublisher(
                notes = noteStore,
                followers = FakeFollowerStore(),
                delivery = TestDelivery(),
            ),
        )
    }

    /**
     * 1 周目だけ失敗させる。繰り返しが失敗で終わっていないことを、
     * 2 周目の投稿が出ることで確かめる
     */
    private class FailingOnceFeedRepository(
        private val delegate: FeedRepository,
    ) : FeedRepository by delegate {
        private var failed = false

        override fun findDue(
            now: Instant,
            limit: Int,
        ): List<Feed> {
            if (!failed) {
                failed = true
                throw IllegalStateException("フィードを引けなかった")
            }
            return delegate.findDue(now, limit)
        }
    }

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-08-16T01:02:03Z")
        val CHECK_INTERVAL = 10.milliseconds
        val TIMEOUT = 10.seconds
        const val FEED_URL = "https://example.com/feed.xml"
        val FEED_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>サンプル</title>
                <link>https://example.com/</link>
                <item><title>1 本目</title><link>https://example.com/1</link></item>
              </channel>
            </rss>
        """.trimIndent()
    }
}
