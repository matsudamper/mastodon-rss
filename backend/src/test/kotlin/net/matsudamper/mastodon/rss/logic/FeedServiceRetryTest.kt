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
import net.matsudamper.mastodon.rss.FakeFeedIcons
import net.matsudamper.mastodon.rss.FakeFollowerStore
import net.matsudamper.mastodon.rss.FakeNoteStore
import net.matsudamper.mastodon.rss.FakeRepositories
import net.matsudamper.mastodon.rss.TestDelivery
import net.matsudamper.mastodon.rss.TestLocalActor
import net.matsudamper.mastodon.rss.feed.FeedFetchService
import net.matsudamper.mastodon.rss.note.NotePublisher
import net.matsudamper.mastodon.rss.repository.FeedItemRepository
import net.matsudamper.mastodon.rss.repository.FeedItemState
import net.matsudamper.mastodon.rss.repository.entity.FeedItemId
import net.matsudamper.mastodon.rss.shared.PublicNoteId

class FeedServiceRetryTest {
    @Test
    fun `配信後に投稿済みの記録で落ちても同じ Note を使う`() = runTest {
        val repositories = FakeRepositories()
        val notes = FakeNoteStore()
        val account = assertNotNull(
            repositories.accounts.add(
                username = TestLocalActor.STORED_USERNAME,
                createdAt = Instant.parse("2026-09-08T00:00:00Z"),
            ),
        )
        val failingFeedItems = FailFirstMarkPosted(repositories.feedItems)
        val firstService = serviceOf(
            repositories = repositories,
            feedItems = failingFeedItems,
            notes = notes,
        )
        firstService.save(accountId = account.id, url = FEED_URL)

        try {
            firstService.postUnpublished(account.id)
            error("markPosted で失敗するはず")
        } catch (e: IllegalStateException) {
            assertEquals(MARK_POSTED_FAILURE, e.message)
        }

        val pending = repositories.feedItems.items().first { it.title == "1 本目" }
        val firstNoteId = assertNotNull(pending.noteId)
        assertEquals(FeedItemState.PENDING, pending.state)
        assertEquals(listOf(firstNoteId.value), notes.added.map { it.publicId.value })

        val result = serviceOf(
            repositories = repositories,
            feedItems = repositories.feedItems,
            notes = notes,
        ).postUnpublished(account.id)

        val success = assertIs<FeedService.PostUnpublishedResult.Success>(result)
        assertEquals(listOf("1 本目", "2 本目"), success.items.map { it.title })
        val retried = repositories.feedItems.items().first { it.title == "1 本目" }
        assertEquals(firstNoteId, retried.noteId)
        assertEquals(FeedItemState.POSTED, retried.state)
        assertEquals(2, notes.added.size)
        assertEquals(2, notes.added.map { it.publicId }.distinct().size)
    }

    private fun serviceOf(
        repositories: FakeRepositories,
        feedItems: FeedItemRepository,
        notes: FakeNoteStore,
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
            feeds = repositories.feeds,
            feedItems = feedItems,
            fetcher = FeedFetchService(HttpClient(engine)),
            actorDirectory = TestLocalActor.directory,
            notePublisher = NotePublisher(
                notes = notes,
                followers = FakeFollowerStore(),
                delivery = TestDelivery(),
            ),
            icons = FakeFeedIcons(),
        )
    }

    private class FailFirstMarkPosted(
        private val delegate: FeedItemRepository,
    ) : FeedItemRepository by delegate {
        private var failed = false

        override fun markPosted(
            id: FeedItemId,
            postedAt: Instant,
            noteId: PublicNoteId,
        ) {
            if (!failed) {
                failed = true
                error(MARK_POSTED_FAILURE)
            }
            delegate.markPosted(id = id, postedAt = postedAt, noteId = noteId)
        }
    }

    private companion object {
        const val FEED_URL = "https://example.com/feed.xml"
        const val MARK_POSTED_FAILURE = "投稿済みの記録に失敗"
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
