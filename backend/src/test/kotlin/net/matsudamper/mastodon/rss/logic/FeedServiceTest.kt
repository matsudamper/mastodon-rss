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
import net.matsudamper.mastodon.rss.FakeFollowerStore
import net.matsudamper.mastodon.rss.FakeNoteStore
import net.matsudamper.mastodon.rss.FakeRepositories
import net.matsudamper.mastodon.rss.TestDelivery
import net.matsudamper.mastodon.rss.TestLocalActor
import net.matsudamper.mastodon.rss.actor.ActorDirectory
import net.matsudamper.mastodon.rss.feed.FeedFetchService
import net.matsudamper.mastodon.rss.note.NotePublisher
import net.matsudamper.mastodon.rss.repository.AccountId
import net.matsudamper.mastodon.rss.repository.FeedItemState

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
            assertEquals(true, success.feed.initialImportDone)
            assertEquals(0, success.postedCount)
            assertEquals(2, success.skippedCount)
            assertEquals(success.feed, repositories.feeds.findByAccountId(account.id))
            assertEquals(
                listOf(FeedItemState.SKIPPED, FeedItemState.SKIPPED),
                repositories.feedItems.items().map { it.state },
            )
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
    fun `フラグメント違いは同じ URL として扱う`() =
        runTest {
            val repositories = FakeRepositories()
            val account1 = assertNotNull(repositories.accounts.add(username = "feed1", createdAt = CREATED_AT))
            val account2 = assertNotNull(repositories.accounts.add(username = "feed2", createdAt = CREATED_AT))
            val service = serviceOf(repositories)
            service.save(accountId = account1.id, url = "$FEED_URL#a")

            assertEquals(FEED_URL, assertNotNull(repositories.feeds.findByAccountId(account1.id)).url)

            val result = service.save(accountId = account2.id, url = "$FEED_URL#b")

            val failure = assertIs<FeedService.SaveResult.Failure>(result)
            assertEquals(FeedService.SaveFailure.DUPLICATE_URL, failure.reason)
        }

    @Test
    fun `ホスト名の末尾に ドット が付いていても同じ URL として扱う`() =
        runTest {
            val repositories = FakeRepositories()
            val account1 = assertNotNull(repositories.accounts.add(username = "feed1", createdAt = CREATED_AT))
            val account2 = assertNotNull(repositories.accounts.add(username = "feed2", createdAt = CREATED_AT))
            val service = serviceOf(repositories)
            service.save(accountId = account1.id, url = "https://example.com./feed.xml")

            assertEquals(FEED_URL, assertNotNull(repositories.feeds.findByAccountId(account1.id)).url)

            val result = service.save(accountId = account2.id, url = FEED_URL)

            val failure = assertIs<FeedService.SaveResult.Failure>(result)
            assertEquals(FeedService.SaveFailure.DUPLICATE_URL, failure.reason)
        }

    @Test
    fun `予約文字でない文字の percent-encoding は元に戻して扱う`() =
        runTest {
            val repositories = FakeRepositories()
            val account1 = assertNotNull(repositories.accounts.add(username = "feed1", createdAt = CREATED_AT))
            val account2 = assertNotNull(repositories.accounts.add(username = "feed2", createdAt = CREATED_AT))
            val service = serviceOf(repositories)
            service.save(accountId = account1.id, url = "https://example.com/%66eed.xml")

            assertEquals(FEED_URL, assertNotNull(repositories.feeds.findByAccountId(account1.id)).url)

            val result = service.save(accountId = account2.id, url = FEED_URL)

            val failure = assertIs<FeedService.SaveResult.Failure>(result)
            assertEquals(FeedService.SaveFailure.DUPLICATE_URL, failure.reason)
        }

    @Test
    fun `残す percent-encoding は 16 進数の綴りを揃えて扱う`() =
        runTest {
            val repositories = FakeRepositories()
            val account1 = assertNotNull(repositories.accounts.add(username = "feed1", createdAt = CREATED_AT))
            val account2 = assertNotNull(repositories.accounts.add(username = "feed2", createdAt = CREATED_AT))
            val service = serviceOf(repositories)
            service.save(accountId = account1.id, url = "https://example.com/a%2fb.xml")

            assertEquals(
                "https://example.com/a%2Fb.xml",
                assertNotNull(repositories.feeds.findByAccountId(account1.id)).url,
            )

            val result = service.save(accountId = account2.id, url = "https://example.com/a%2Fb.xml")

            val failure = assertIs<FeedService.SaveResult.Failure>(result)
            assertEquals(FeedService.SaveFailure.DUPLICATE_URL, failure.reason)
        }

    @Test
    fun `ホスト名の綴りが違っても同じ URL として扱う`() =
        runTest {
            val repositories = FakeRepositories()
            val account1 = assertNotNull(repositories.accounts.add(username = "feed1", createdAt = CREATED_AT))
            val account2 = assertNotNull(repositories.accounts.add(username = "feed2", createdAt = CREATED_AT))
            val service = serviceOf(repositories)
            service.save(accountId = account1.id, url = "https://EXAMPLE.com/feed.xml")

            assertEquals(FEED_URL, assertNotNull(repositories.feeds.findByAccountId(account1.id)).url)

            val result = service.save(accountId = account2.id, url = FEED_URL)

            val failure = assertIs<FeedService.SaveResult.Failure>(result)
            assertEquals(FeedService.SaveFailure.DUPLICATE_URL, failure.reason)
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

    @Test
    fun `スキームの無い YouTube の URL もプレビューできる`() =
        runTest {
            val service = serviceOf(FakeRepositories())

            val result = service.preview("youtube.com/channel/UCabcdefghijklmnopqrstuv")

            assertIs<FeedService.PreviewResult.Success>(result)
        }

    @Test
    fun `登録時に投稿しないと選ぶと記事は SKIPPED になる`() =
        runTest {
            val repositories = FakeRepositories()
            val account = assertNotNull(repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT))
            val service = serviceOf(repositories)

            val result = service.save(accountId = account.id, url = FEED_URL, postExistingItems = false)

            val success = assertIs<FeedService.SaveResult.Success>(result)
            assertEquals(0, success.postedCount)
            assertEquals(2, success.skippedCount)
            assertEquals(true, success.feed.initialImportDone)
            assertEquals(
                listOf(FeedItemState.SKIPPED, FeedItemState.SKIPPED),
                repositories.feedItems.items().map { it.state },
            )
        }

    @Test
    fun `登録時に既存記事も投稿すると notes に残る`() =
        runTest {
            val repositories = FakeRepositories()
            val noteStore = FakeNoteStore()
            val account = assertNotNull(repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT))
            val service = serviceOf(repositories, noteStore = noteStore)

            val result = service.save(accountId = account.id, url = FEED_URL, postExistingItems = true)

            val success = assertIs<FeedService.SaveResult.Success>(result)
            assertEquals(2, success.postedCount)
            assertEquals(0, success.skippedCount)
            assertEquals(
                listOf(FeedItemState.POSTED, FeedItemState.POSTED),
                repositories.feedItems.items().map { it.state },
            )
            assertEquals(
                listOf(TestLocalActor.STORED_USERNAME, TestLocalActor.STORED_USERNAME),
                noteStore.added.map { it.username },
            )
            assertEquals(
                listOf("https://example.com/1", "https://example.com/2"),
                noteStore.added.map { html ->
                    Regex("""href="([^"]+)"""").find(html.contentHtml)?.groupValues?.get(1)
                },
            )
            assertEquals(
                listOf("1 本目", "2 本目"),
                noteStore.added.map { html ->
                    Regex(""">([^<]+)</a>""").find(html.contentHtml)?.groupValues?.get(1)
                },
            )
        }

    @Test
    fun `相対リンクはフィード URL を基準に絶対化して投稿する`() =
        runTest {
            val repositories = FakeRepositories()
            val noteStore = FakeNoteStore()
            val account = assertNotNull(repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT))
            val service = serviceOf(
                repositories,
                xml = RELATIVE_LINK_XML,
                noteStore = noteStore,
            )

            val result = service.save(accountId = account.id, url = FEED_URL, postExistingItems = true)

            val success = assertIs<FeedService.SaveResult.Success>(result)
            assertEquals(1, success.postedCount)
            assertEquals(0, success.skippedCount)
            assertEquals(
                listOf("https://example.com/posts/1"),
                noteStore.added.map { html ->
                    Regex("""href="([^"]+)"""").find(html.contentHtml)?.groupValues?.get(1)
                },
            )
        }

    @Test
    fun `題名もリンクも無い記事は投稿するを選んでも SKIPPED になる`() =
        runTest {
            val repositories = FakeRepositories()
            val noteStore = FakeNoteStore()
            val account = assertNotNull(repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT))
            val service = serviceOf(
                repositories,
                xml = EMPTY_ITEM_XML,
                noteStore = noteStore,
            )

            val result = service.save(accountId = account.id, url = FEED_URL, postExistingItems = true)

            val success = assertIs<FeedService.SaveResult.Success>(result)
            assertEquals(1, success.postedCount)
            assertEquals(1, success.skippedCount)
            assertEquals(
                listOf(FeedItemState.SKIPPED, FeedItemState.POSTED),
                repositories.feedItems.items().map { it.state },
            )
            assertEquals(1, noteStore.added.size)
        }

    @Test
    fun `アクターを引けなければ PENDING のまま残す`() =
        runTest {
            val repositories = FakeRepositories()
            val noteStore = FakeNoteStore()
            val account = assertNotNull(repositories.accounts.add(username = "feed2", createdAt = CREATED_AT))
            val service = serviceOf(repositories, noteStore = noteStore)

            val result = service.save(accountId = account.id, url = FEED_URL, postExistingItems = true)

            val success = assertIs<FeedService.SaveResult.Success>(result)
            assertEquals(0, success.postedCount)
            assertEquals(0, success.skippedCount)
            assertEquals(
                listOf(FeedItemState.PENDING, FeedItemState.PENDING),
                repositories.feedItems.items().map { it.state },
            )
            assertEquals(0, noteStore.added.size)
        }

    private fun serviceOf(
        repositories: FakeRepositories,
        status: HttpStatusCode = HttpStatusCode.OK,
        xml: String = FEED_XML,
        noteStore: FakeNoteStore = FakeNoteStore(),
        actorDirectory: ActorDirectory = TestLocalActor.directory,
    ): FeedService {
        val engine = MockEngine {
            respond(
                content = if (status == HttpStatusCode.OK) xml else "",
                status = status,
                headers = headersOf("Content-Type", "application/rss+xml"),
            )
        }

        return FeedService(
            accounts = repositories.accounts,
            feeds = repositories.feeds,
            feedItems = repositories.feedItems,
            fetcher = FeedFetchService(HttpClient(engine)),
            actorDirectory = actorDirectory,
            notePublisher = NotePublisher(
                notes = noteStore,
                followers = FakeFollowerStore(),
                delivery = TestDelivery(),
            ),
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
        val RELATIVE_LINK_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>サンプル</title>
              <link href="https://example.com/"/>
              <entry>
                <title>1 本目</title>
                <id>urn:uuid:1</id>
                <link href="/posts/1"/>
              </entry>
            </feed>
        """.trimIndent()
        val EMPTY_ITEM_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>サンプル</title>
                <link>https://example.com/</link>
                <item></item>
                <item><title>1 本目</title><link>https://example.com/1</link></item>
              </channel>
            </rss>
        """.trimIndent()
    }
}
