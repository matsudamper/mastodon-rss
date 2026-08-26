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
import io.ktor.client.engine.mock.respondRedirect
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
import net.matsudamper.mastodon.rss.repository.FeedItemState
import net.matsudamper.mastodon.rss.shared.AccountId

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
            assertEquals(success.feed, repositories.feeds.findByAccountId(account.id))
            assertEquals(
                listOf(FeedItemState.PENDING, FeedItemState.PENDING),
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
    fun `登録した記事は未投稿のまま残る`() =
        runTest {
            val repositories = FakeRepositories()
            val noteStore = FakeNoteStore()
            val account = assertNotNull(repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT))
            val service = serviceOf(repositories, noteStore = noteStore)

            val result = service.save(accountId = account.id, url = FEED_URL)

            val success = assertIs<FeedService.SaveResult.Success>(result)
            assertEquals(true, success.feed.initialImportDone)
            assertEquals(
                listOf(FeedItemState.PENDING, FeedItemState.PENDING),
                repositories.feedItems.items().map { it.state },
            )
            assertEquals(0, noteStore.added.size)
        }

    @Test
    fun `未投稿の記事を投稿すると notes に残る`() =
        runTest {
            val repositories = FakeRepositories()
            val noteStore = FakeNoteStore()
            val account = assertNotNull(repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT))
            val service = serviceOf(repositories, noteStore = noteStore)
            service.save(accountId = account.id, url = FEED_URL)

            val result = service.postUnpublished(account.id)

            val success = assertIs<FeedService.PostUnpublishedResult.Success>(result)
            assertEquals(listOf("1 本目", "2 本目"), success.items.map { it.title })
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
                listOf(
                    """<p>1 本目<br><a href="https://example.com/1">https://example.com/1</a></p>""",
                    """<p>2 本目<br><a href="https://example.com/2">https://example.com/2</a></p>""",
                ),
                noteStore.added.map { it.contentHtml },
            )
        }

    @Test
    fun `未投稿の記事を取得できる`() =
        runTest {
            val repositories = FakeRepositories()
            val account = assertNotNull(repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT))
            val service = serviceOf(repositories)
            service.save(accountId = account.id, url = FEED_URL)

            val result = service.unpublishedItems(account.id)

            val success = assertIs<FeedService.UnpublishedResult.Success>(result)
            assertEquals(listOf("1 本目", "2 本目"), success.items.map { it.title })
            assertEquals(listOf("https://example.com/1", "https://example.com/2"), success.items.map { it.link })
        }

    @Test
    fun `投稿した記事は投稿の id から引ける`() =
        runTest {
            val repositories = FakeRepositories()
            val noteStore = FakeNoteStore()
            val account = assertNotNull(repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT))
            val service = serviceOf(repositories, noteStore = noteStore)
            service.save(accountId = account.id, url = FEED_URL)
            service.postUnpublished(account.id)
            val noteIds = noteStore.added.map { it.publicId }

            val items = service.itemsByNoteIds(noteIds)

            assertEquals(noteIds.toSet(), items.keys)
            assertEquals(
                listOf("1 本目", "2 本目"),
                noteIds.map { assertNotNull(items[it]).title },
            )
        }

    @Test
    fun `記事を消すと未投稿として取り込み直される`() =
        runTest {
            val repositories = FakeRepositories()
            val noteStore = FakeNoteStore()
            val account = assertNotNull(repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT))
            val service = serviceOf(repositories, noteStore = noteStore)
            service.save(accountId = account.id, url = FEED_URL)
            service.postUnpublished(account.id)
            val posted = repositories.feedItems.items().first { it.title == "1 本目" }

            val deleted = service.deleteItem(accountId = account.id, feedItemId = posted.id)

            assertEquals(posted.id, assertIs<FeedService.DeleteItemResult.Success>(deleted).deletedId)

            val result = service.postUnpublished(account.id)

            assertEquals(
                listOf("1 本目"),
                assertIs<FeedService.PostUnpublishedResult.Success>(result).items.map { it.title },
            )
            assertEquals(3, noteStore.added.size)
        }

    @Test
    fun `他のアカウントのフィードの記事は消せない`() =
        runTest {
            val repositories = FakeRepositories()
            val owner = assertNotNull(repositories.accounts.add(username = "feed1", createdAt = CREATED_AT))
            val other = assertNotNull(repositories.accounts.add(username = "feed2", createdAt = CREATED_AT))
            val service = serviceOf(repositories)
            service.save(accountId = owner.id, url = FEED_URL)
            service.save(accountId = other.id, url = "https://example.com/other.xml")
            val item = repositories.feedItems.items().first { it.feedId == assertNotNull(repositories.feeds.findByAccountId(owner.id)).id }

            val result = service.deleteItem(accountId = other.id, feedItemId = item.id)

            assertEquals(
                FeedService.DeleteItemFailure.NOT_FOUND,
                assertIs<FeedService.DeleteItemResult.Failure>(result).reason,
            )
            assertNotNull(repositories.feedItems.items().firstOrNull { it.id == item.id })
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
            service.save(accountId = account.id, url = FEED_URL)

            val result = service.postUnpublished(account.id)

            val success = assertIs<FeedService.PostUnpublishedResult.Success>(result)
            assertEquals(listOf("1 本目"), success.items.map { it.title })
            assertEquals(
                listOf("https://example.com/posts/1"),
                noteStore.added.map { html ->
                    Regex("""href="([^"]+)"""").find(html.contentHtml)?.groupValues?.get(1)
                },
            )
        }

    @Test
    fun `題名もリンクも無い記事は未投稿に入らない`() =
        runTest {
            val repositories = FakeRepositories()
            val noteStore = FakeNoteStore()
            val account = assertNotNull(repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT))
            val service = serviceOf(
                repositories,
                xml = EMPTY_ITEM_XML,
                noteStore = noteStore,
            )
            service.save(accountId = account.id, url = FEED_URL)

            val unpublished = assertIs<FeedService.UnpublishedResult.Success>(service.unpublishedItems(account.id))
            assertEquals(listOf("1 本目"), unpublished.items.map { it.title })

            val result = service.postUnpublished(account.id)

            val success = assertIs<FeedService.PostUnpublishedResult.Success>(result)
            assertEquals(listOf("1 本目"), success.items.map { it.title })
            assertEquals(
                listOf(FeedItemState.SKIPPED, FeedItemState.POSTED),
                repositories.feedItems.items().map { it.state },
            )
            assertEquals(1, noteStore.added.size)
        }

    @Test
    fun `投稿本文は題名と説明とリンクを改行で並べる`() =
        runTest {
            val repositories = FakeRepositories()
            val noteStore = FakeNoteStore()
            val account = assertNotNull(repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT))
            val service = serviceOf(
                repositories,
                xml = DESCRIPTION_ITEM_XML,
                noteStore = noteStore,
            )
            service.save(accountId = account.id, url = FEED_URL)

            val result = service.postUnpublished(account.id)

            val success = assertIs<FeedService.PostUnpublishedResult.Success>(result)
            assertEquals(listOf("1 本目"), success.items.map { it.title })
            assertEquals(
                listOf(
                    """<p>1 本目<br>記事の要約<br><a href="https://example.com/1">https://example.com/1</a></p>""",
                ),
                noteStore.added.map { it.contentHtml },
            )
        }

    @Test
    fun `取り込み時に無かった説明も投稿直前の再取得で載せる`() =
        runTest {
            val repositories = FakeRepositories()
            val noteStore = FakeNoteStore()
            val account = assertNotNull(repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT))
            val service = serviceOf(
                repositories,
                xmls = listOf(FEED_XML, DESCRIPTION_ITEM_XML),
                noteStore = noteStore,
            )
            service.save(accountId = account.id, url = FEED_URL)

            val result = service.postUnpublished(account.id)

            val success = assertIs<FeedService.PostUnpublishedResult.Success>(result)
            assertEquals(
                listOf(
                    """<p>1 本目<br>記事の要約<br><a href="https://example.com/1">https://example.com/1</a></p>""",
                    """<p>2 本目<br><a href="https://example.com/2">https://example.com/2</a></p>""",
                ),
                noteStore.added.map { it.contentHtml },
            )
            assertEquals(listOf("1 本目", "2 本目"), success.items.map { it.title })
        }

    @Test
    fun `アクターを引けなければ PENDING のまま残す`() =
        runTest {
            val repositories = FakeRepositories()
            val noteStore = FakeNoteStore()
            val account = assertNotNull(repositories.accounts.add(username = "feed2", createdAt = CREATED_AT))
            val service = serviceOf(repositories, noteStore = noteStore)
            service.save(accountId = account.id, url = FEED_URL)

            val result = service.postUnpublished(account.id)

            val success = assertIs<FeedService.PostUnpublishedResult.Success>(result)
            assertEquals(emptyList(), success.items)
            assertEquals(
                listOf(FeedItemState.PENDING, FeedItemState.PENDING),
                repositories.feedItems.items().map { it.state },
            )
            assertEquals(0, noteStore.added.size)
        }

    @Test
    fun `投稿前に最新の記事を取り込む`() =
        runTest {
            val repositories = FakeRepositories()
            val noteStore = FakeNoteStore()
            val account = assertNotNull(repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT))
            val service = serviceOf(
                repositories,
                xmls = listOf(FEED_XML, LATEST_XML),
                noteStore = noteStore,
            )
            service.save(accountId = account.id, url = FEED_URL)

            val result = service.postUnpublished(account.id)

            val success = assertIs<FeedService.PostUnpublishedResult.Success>(result)
            assertEquals(listOf("1 本目", "2 本目", "3 本目"), success.items.map { it.title })
            assertEquals(3, noteStore.added.size)
        }

    @Test
    fun `最新の取り込みに失敗したら投稿しない`() =
        runTest {
            val repositories = FakeRepositories()
            val noteStore = FakeNoteStore()
            val account = assertNotNull(repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT))
            val service = serviceOf(
                repositories,
                statuses = listOf(HttpStatusCode.OK, HttpStatusCode.NotFound),
                noteStore = noteStore,
            )
            service.save(accountId = account.id, url = FEED_URL)

            val result = service.postUnpublished(account.id)

            val failure = assertIs<FeedService.PostUnpublishedResult.Failure>(result)
            assertEquals(FeedService.PostUnpublishedFailure.FETCH_FAILED, failure.reason)
            assertEquals(
                listOf(FeedItemState.PENDING, FeedItemState.PENDING),
                repositories.feedItems.items().map { it.state },
            )
            assertEquals(0, noteStore.added.size)
        }

    @Test
    fun `最新取得の最終 URL を基準に相対リンクを絶対化する`() =
        runTest {
            val repositories = FakeRepositories()
            val noteStore = FakeNoteStore()
            val account = assertNotNull(repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT))
            var firstFetch = true
            val engine = MockEngine { request ->
                when {
                    firstFetch -> {
                        firstFetch = false
                        respond(
                            content = FEED_XML,
                            status = HttpStatusCode.OK,
                            headers = headersOf("Content-Type", "application/rss+xml"),
                        )
                    }

                    request.url.host == "example.com" -> respondRedirect(REDIRECTED_FEED_URL)

                    else -> respond(
                        content = REDIRECTED_RELATIVE_LINK_XML,
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/rss+xml"),
                    )
                }
            }
            val service = serviceOf(repositories, noteStore = noteStore, engine = engine)
            service.save(accountId = account.id, url = FEED_URL)

            val result = service.postUnpublished(account.id)

            val success = assertIs<FeedService.PostUnpublishedResult.Success>(result)
            assertEquals(
                listOf("https://example.com/1", "https://example.com/2", "https://cdn.example.net/posts/3"),
                noteStore.added.map { html ->
                    Regex("""href="([^"]+)"""").find(html.contentHtml)?.groupValues?.get(1)
                },
            )
            assertEquals(listOf("1 本目", "2 本目", "3 本目"), success.items.map { it.title })
        }

    private fun serviceOf(
        repositories: FakeRepositories,
        status: HttpStatusCode = HttpStatusCode.OK,
        xml: String = FEED_XML,
        xmls: List<String>? = null,
        statuses: List<HttpStatusCode>? = null,
        noteStore: FakeNoteStore = FakeNoteStore(),
        actorDirectory: ActorDirectory = TestLocalActor.directory,
        engine: MockEngine? = null,
    ): FeedService {
        val mockEngine = engine ?: run {
            val bodies = ArrayDeque(xmls ?: listOf(xml))
            val codes = ArrayDeque(statuses ?: listOf(status))
            MockEngine {
                val code = if (codes.size > 1) codes.removeFirst() else codes.first()
                val body = if (bodies.size > 1) bodies.removeFirst() else bodies.first()
                respond(
                    content = if (code == HttpStatusCode.OK) body else "",
                    status = code,
                    headers = headersOf("Content-Type", "application/rss+xml"),
                )
            }
        }

        return FeedService(
            accounts = repositories.accounts,
            feeds = repositories.feeds,
            feedItems = repositories.feedItems,
            fetcher = FeedFetchService(HttpClient(mockEngine)),
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
        const val REDIRECTED_FEED_URL = "https://cdn.example.net/rss/feed.xml"
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
        val LATEST_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>サンプル</title>
                <link>https://example.com/</link>
                <item><title>1 本目</title><link>https://example.com/1</link></item>
                <item><title>2 本目</title><link>https://example.com/2</link></item>
                <item><title>3 本目</title><link>https://example.com/3</link></item>
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
        val DESCRIPTION_ITEM_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>サンプル</title>
                <link>https://example.com/</link>
                <item>
                  <title>1 本目</title>
                  <link>https://example.com/1</link>
                  <description>記事の要約</description>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val REDIRECTED_RELATIVE_LINK_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>サンプル</title>
                <link>https://cdn.example.net/</link>
                <item><title>1 本目</title><link>https://example.com/1</link></item>
                <item><title>2 本目</title><link>https://example.com/2</link></item>
                <item><title>3 本目</title><link>/posts/3</link></item>
              </channel>
            </rss>
        """.trimIndent()
    }
}
