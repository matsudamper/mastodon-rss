package net.matsudamper.mastodon.rss.logic

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondRedirect
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import net.matsudamper.mastodon.rss.FakeFeedIcons
import net.matsudamper.mastodon.rss.FakeFollowerStore
import net.matsudamper.mastodon.rss.FakeNoteStore
import net.matsudamper.mastodon.rss.FakeRepositories
import net.matsudamper.mastodon.rss.FakeStoredActorNames
import net.matsudamper.mastodon.rss.TestDelivery
import net.matsudamper.mastodon.rss.TestLocalActor
import net.matsudamper.mastodon.rss.TestWebPageUrls
import net.matsudamper.mastodon.rss.actor.ActorDirectory
import net.matsudamper.mastodon.rss.feed.FeedFetchService
import net.matsudamper.mastodon.rss.note.NotePublisher
import net.matsudamper.mastodon.rss.repository.Account
import net.matsudamper.mastodon.rss.repository.AccountRepository
import net.matsudamper.mastodon.rss.repository.FeedFetchValidators
import net.matsudamper.mastodon.rss.repository.FeedItemState
import net.matsudamper.mastodon.rss.repository.entity.FeedId
import net.matsudamper.mastodon.rss.shared.AccountId
import net.matsudamper.mastodon.rss.shared.PublicNoteId

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
            assertEquals(emptyList(), repositories.feedItems.items())
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
    fun `取り込みが終わらなかった登録はやり直せる`() =
        runTest {
            val repositories = FakeRepositories()
            val account = assertNotNull(repositories.accounts.add(username = "feed1", createdAt = CREATED_AT))
            val service = serviceOf(repositories)
            service.save(accountId = account.id, url = FEED_URL)
            repositories.feeds.clearInitialImportDone(assertNotNull(repositories.feeds.findByAccountId(account.id)).id)

            val result = service.save(accountId = account.id, url = FEED_URL)

            val success = assertIs<FeedService.SaveResult.Success>(result)
            assertEquals(true, success.feed.initialImportDone)
        }

    @Test
    fun `やり直した登録が取れなければ前のフィードを残す`() =
        runTest {
            val repositories = FakeRepositories()
            val account = assertNotNull(repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT))
            val service = serviceOf(repositories)
            val saved = assertIs<FeedService.SaveResult.Success>(service.save(accountId = account.id, url = FEED_URL))
            service.postUnpublished(account.id)
            repositories.feeds.clearInitialImportDone(saved.feed.id)
            val before = assertNotNull(repositories.feeds.findByAccountId(account.id))

            val result = serviceOf(repositories, status = HttpStatusCode.NotFound).save(accountId = account.id, url = FEED_URL)

            val failure = assertIs<FeedService.SaveResult.Failure>(result)
            assertEquals(FeedService.SaveFailure.FETCH_FAILED, failure.reason)
            assertEquals(before, repositories.feeds.findByAccountId(account.id))
            assertEquals(
                listOf(FeedItemState.POSTED, FeedItemState.POSTED),
                repositories.feedItems.items().map { it.state },
            )
        }

    @Test
    fun `やり直した登録が他のアカウントと重複したら前のフィードを残す`() =
        runTest {
            val repositories = FakeRepositories()
            val account1 = assertNotNull(repositories.accounts.add(username = "feed1", createdAt = CREATED_AT))
            val account2 = assertNotNull(repositories.accounts.add(username = "feed2", createdAt = CREATED_AT))
            val service = serviceOf(repositories)
            val saved = assertIs<FeedService.SaveResult.Success>(service.save(accountId = account1.id, url = "https://example.com/other.xml"))
            repositories.feeds.clearInitialImportDone(saved.feed.id)
            service.save(accountId = account2.id, url = FEED_URL)

            val result = service.save(accountId = account1.id, url = FEED_URL)

            val failure = assertIs<FeedService.SaveResult.Failure>(result)
            assertEquals(FeedService.SaveFailure.DUPLICATE_URL, failure.reason)
            assertEquals(saved.feed.id, assertNotNull(repositories.feeds.findByAccountId(account1.id)).id)
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
            assertEquals(listOf("1 本目"), success.preview.sampleItems.map { it.title })
        }

    @Test
    fun `プレビューの見本は古い順のフィードでも最新の記事になる`() =
        runTest {
            val service = serviceOf(FakeRepositories(), xml = OLDEST_FIRST_XML)

            val result = service.preview(FEED_URL)

            val success = assertIs<FeedService.PreviewResult.Success>(result)
            assertEquals(listOf("新しい記事"), success.preview.sampleItems.map { it.title })
        }

    @Test
    fun `プレビューは切り詰めた説明と配信元のままの説明を両方返す`() =
        runTest {
            val service = serviceOf(FakeRepositories(), xml = LONG_DESCRIPTION_XML)

            val result = service.preview(FEED_URL)

            val success = assertIs<FeedService.PreviewResult.Success>(result)
            val description = assertNotNull(success.preview.description)
            val fullDescription = assertNotNull(success.preview.fullDescription)

            // 一覧に並べる方は 1 行に潰して切り詰める
            assertEquals(false, description.contains("\n"))
            assertEquals(true, description.length < fullDescription.length)

            // プロフィールに取り込む方は段落を残したまま渡す
            assertEquals(true, fullDescription.contains("\n"))
            assertEquals(true, fullDescription.endsWith("最後の段落"))
        }

    @Test
    fun `スキームの無い YouTube の URL もプレビューできる`() =
        runTest {
            val service = serviceOf(FakeRepositories())

            val result = service.preview("youtube.com/channel/UCabcdefghijklmnopqrstuv")

            assertIs<FeedService.PreviewResult.Success>(result)
        }

    @Test
    fun `登録時に既存の記事を保存しない`() =
        runTest {
            val repositories = FakeRepositories()
            val noteStore = FakeNoteStore()
            val account = assertNotNull(repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT))
            val service = serviceOf(repositories, noteStore = noteStore)

            val result = service.save(accountId = account.id, url = FEED_URL)

            val success = assertIs<FeedService.SaveResult.Success>(result)
            assertEquals(true, success.feed.initialImportDone)
            assertEquals(emptyList(), repositories.feedItems.items())
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
            assertEquals(2, success.importedCount)
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
    fun `登録直後の記事は未投稿として扱わない`() =
        runTest {
            val repositories = FakeRepositories()
            val account = assertNotNull(repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT))
            val service = serviceOf(repositories)
            service.save(accountId = account.id, url = FEED_URL)

            val result = service.unpublishedItems(account.id)

            val success = assertIs<FeedService.UnpublishedResult.Success>(result)
            assertEquals(emptyList(), success.items)
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
            val noteIds = noteStore.added.map { PublicNoteId(it.publicId.value) }

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

            val deleted = service.deleteItems(accountId = account.id, feedItemIds = listOf(posted.id))

            assertEquals(listOf(posted.id), assertIs<FeedService.DeleteItemsResult.Success>(deleted).deletedIds)

            val result = service.postUnpublished(account.id)

            assertEquals(1, assertIs<FeedService.PostUnpublishedResult.Success>(result).importedCount)
            assertEquals(3, noteStore.added.size)
        }

    @Test
    fun `記事をまとめて消せる`() =
        runTest {
            val repositories = FakeRepositories()
            val account = assertNotNull(repositories.accounts.add(username = "feed1", createdAt = CREATED_AT))
            val service = serviceOf(repositories)
            service.save(accountId = account.id, url = FEED_URL)
            service.postUnpublished(account.id)
            val items = repositories.feedItems.items()

            val result = service.deleteItems(accountId = account.id, feedItemIds = items.map { it.id })

            assertEquals(
                items.map { it.id },
                assertIs<FeedService.DeleteItemsResult.Success>(result).deletedIds,
            )
            assertEquals(emptyList(), repositories.feedItems.items())
        }

    @Test
    fun `1 件でも他のフィードの記事が混ざっていたら何も消さない`() =
        runTest {
            val repositories = FakeRepositories()
            val owner = assertNotNull(repositories.accounts.add(username = "feed1", createdAt = CREATED_AT))
            val other = assertNotNull(repositories.accounts.add(username = "feed2", createdAt = CREATED_AT))
            val service = serviceOf(repositories)
            service.save(accountId = owner.id, url = FEED_URL)
            service.save(accountId = other.id, url = "https://example.com/other.xml")
            service.postUnpublished(owner.id)
            service.postUnpublished(other.id)
            val ownerFeedId = assertNotNull(repositories.feeds.findByAccountId(owner.id)).id
            val mine = repositories.feedItems.items().first { it.feedId == ownerFeedId }
            val theirs = repositories.feedItems.items().first { it.feedId != ownerFeedId }

            val result = service.deleteItems(accountId = owner.id, feedItemIds = listOf(mine.id, theirs.id))

            assertEquals(
                FeedService.DeleteItemsFailure.NOT_FOUND,
                assertIs<FeedService.DeleteItemsResult.Failure>(result).reason,
            )
            assertNotNull(repositories.feedItems.items().firstOrNull { it.id == mine.id })
            assertNotNull(repositories.feedItems.items().firstOrNull { it.id == theirs.id })
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
            service.postUnpublished(owner.id)
            val item = repositories.feedItems.items().first { it.feedId == assertNotNull(repositories.feeds.findByAccountId(owner.id)).id }

            val result = service.deleteItems(accountId = other.id, feedItemIds = listOf(item.id))

            assertEquals(
                FeedService.DeleteItemsFailure.NOT_FOUND,
                assertIs<FeedService.DeleteItemsResult.Failure>(result).reason,
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
            assertEquals(1, success.importedCount)
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
            assertEquals(emptyList(), unpublished.items)

            val result = service.postUnpublished(account.id)

            val success = assertIs<FeedService.PostUnpublishedResult.Success>(result)
            assertEquals(2, success.importedCount)
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
            assertEquals(1, success.importedCount)
            assertEquals(
                listOf(
                    """<p>1 本目<br>記事の要約<br><a href="https://example.com/1">https://example.com/1</a></p>""",
                ),
                noteStore.added.map { it.contentHtml },
            )
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
            assertEquals(2, success.importedCount)
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
            assertEquals(3, success.importedCount)
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
            assertEquals(emptyList(), repositories.feedItems.items())
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
            assertEquals(3, success.importedCount)
        }

    @Test
    fun `投稿前の再取得でアイコンを最新に入れ替える`() =
        runTest {
            val repositories = FakeRepositories()
            val account = assertNotNull(repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT))
            val service = serviceOf(
                repositories,
                xmls = listOf(ICON_XML, CHANGED_ICON_XML),
            )
            service.save(accountId = account.id, url = FEED_URL)
            assertEquals(
                "https://example.com/icon.png",
                assertNotNull(repositories.feeds.findByAccountId(account.id)).iconUrl,
            )

            service.postUnpublished(account.id)

            assertEquals(
                "https://example.com/icon2.png",
                assertNotNull(repositories.feeds.findByAccountId(account.id)).iconUrl,
            )
        }

    @Test
    fun `取得の時期が来たフィードの新着を投稿する`() =
        runTest {
            val repositories = FakeRepositories()
            val noteStore = FakeNoteStore()
            val account = assertNotNull(repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT))
            val service = serviceOf(
                repositories,
                xmls = listOf(FEED_XML, FEED_XML, LATEST_XML),
                noteStore = noteStore,
            )
            service.save(accountId = account.id, url = FEED_URL)
            service.pollDue(now = Instant.now().plusSeconds(DUE_AFTER_SECONDS), limit = 10)

            val results = service.pollDue(now = Instant.now().plusSeconds(DUE_AFTER_SECONDS * 2), limit = 10)

            assertEquals(listOf(null), results.map { it.error })
            assertEquals(listOf("3 本目"), results.single().postedItems.map { it.title })
            assertEquals(3, noteStore.added.size)
            val fetch = assertNotNull(repositories.feeds.findByAccountId(account.id)).fetch
            assertEquals(null, fetch.lastError)
            assertNotNull(fetch.lastSucceededAt)
        }

    @Test
    fun `登録前からある記事も最初の定期ポーリングで投稿する`() =
        runTest {
            val repositories = FakeRepositories()
            val noteStore = FakeNoteStore()
            val account = assertNotNull(repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT))
            val service = serviceOf(repositories, noteStore = noteStore)
            service.save(accountId = account.id, url = FEED_URL)

            val results = service.pollDue(now = Instant.now().plusSeconds(DUE_AFTER_SECONDS), limit = 10)

            assertEquals(listOf("1 本目", "2 本目"), results.single().postedItems.map { it.title })
            assertEquals(2, noteStore.added.size)
            assertEquals(
                listOf(FeedItemState.POSTED, FeedItemState.POSTED),
                repositories.feedItems.items().map { it.state },
            )
        }

    @Test
    fun `定期ポーリングでもアイコンを最新に入れ替える`() =
        runTest {
            val repositories = FakeRepositories()
            val account = assertNotNull(repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT))
            val icons = FakeFeedIcons()
            val service = serviceOf(
                repositories,
                xmls = listOf(ICON_XML, CHANGED_ICON_XML),
                icons = icons,
            )
            service.save(accountId = account.id, url = FEED_URL)

            service.pollDue(now = Instant.now().plusSeconds(DUE_AFTER_SECONDS), limit = 10)

            val feed = assertNotNull(repositories.feeds.findByAccountId(account.id))
            assertEquals("https://example.com/icon2.png", feed.iconUrl)
            // 中身も取り込みに合わせて入れ替える。見に来たときには取りに行かない
            assertEquals(listOf(feed.id, feed.id), icons.refreshed.map { it.first })
            assertEquals(
                listOf<String?>("https://example.com/icon.png", "https://example.com/icon2.png"),
                icons.refreshed.map { it.second },
            )
        }

    @Test
    fun `アイコンが拾えなかった取り込みでは前の URL を残す`() =
        runTest {
            val repositories = FakeRepositories()
            val account = assertNotNull(repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT))
            val icons = FakeFeedIcons()
            val service = serviceOf(
                repositories,
                xmls = listOf(ICON_XML, FEED_XML),
                icons = icons,
            )
            service.save(accountId = account.id, url = FEED_URL)

            service.pollDue(now = Instant.now().plusSeconds(DUE_AFTER_SECONDS), limit = 10)

            // 空で上書きすると、拾えなかった 1 回でアイコンが消える
            assertEquals(
                "https://example.com/icon.png",
                assertNotNull(repositories.feeds.findByAccountId(account.id)).iconUrl,
            )
            assertEquals(
                listOf<String?>("https://example.com/icon.png", "https://example.com/icon.png"),
                icons.refreshed.map { it.second },
            )
        }

    @Test
    fun `アイコンを入れ替えられなくても記事は取り込む`() =
        runTest {
            val repositories = FakeRepositories()
            val noteStore = FakeNoteStore()
            val account = assertNotNull(repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT))
            val service = serviceOf(
                repositories,
                xmls = listOf(ICON_XML, ICON_XML),
                noteStore = noteStore,
                icons = object : FeedIcons {
                    override suspend fun refresh(
                        feedId: FeedId,
                        iconUrl: String?,
                    ) {
                        error("アイコンを置けなかった")
                    }
                },
            )
            service.save(accountId = account.id, url = FEED_URL)

            val results = service.pollDue(now = Instant.now().plusSeconds(DUE_AFTER_SECONDS), limit = 10)

            assertEquals(listOf(null), results.map { it.error })
            assertEquals(1, noteStore.added.size)
        }

    @Test
    fun `アイコンを名乗らなくなったフィードでも前の URL を残す`() =
        runTest {
            val repositories = FakeRepositories()
            val account = assertNotNull(repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT))
            val service = serviceOf(
                repositories,
                xmls = listOf(ICON_XML, FEED_XML),
            )
            service.save(accountId = account.id, url = FEED_URL)

            service.postUnpublished(account.id)

            // 拾えなかった 1 回でアイコンが消えるのを避ける。取り下げと拾えなかったのは区別できない
            assertEquals(
                "https://example.com/icon.png",
                assertNotNull(repositories.feeds.findByAccountId(account.id)).iconUrl,
            )
        }

    @Test
    fun `投稿できなかった記事は次の取得で投稿し直す`() =
        runTest {
            val repositories = FakeRepositories()
            val noteStore = FakeNoteStore()
            val account = assertNotNull(repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT))
            val unknownActor = ActorDirectory(
                domain = TestLocalActor.DOMAIN,
                stored = FakeStoredActorNames(storedUserNames = emptyList()),
            )
            val failing = serviceOf(repositories, noteStore = noteStore, actorDirectory = unknownActor)
            failing.save(accountId = account.id, url = FEED_URL)
            failing.pollDue(now = Instant.now().plusSeconds(DUE_AFTER_SECONDS), limit = 10)
            assertEquals(0, noteStore.added.size)

            val results = serviceOf(repositories, noteStore = noteStore)
                .pollDue(now = Instant.now().plusSeconds(DUE_AFTER_SECONDS * 2), limit = 10)

            assertEquals(listOf("1 本目", "2 本目"), results.single().postedItems.map { it.title })
            assertEquals(2, noteStore.added.size)
        }

    @Test
    fun `1 本が例外で落ちても残りのフィードを続ける`() =
        runTest {
            val repositories = FakeRepositories()
            val noteStore = FakeNoteStore()
            val broken = assertNotNull(repositories.accounts.add(username = TestLocalActor.USERNAME, createdAt = CREATED_AT))
            val healthy = assertNotNull(repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT))
            val service = serviceOf(repositories, noteStore = noteStore)
            service.save(accountId = broken.id, url = FEED_URL)
            service.save(accountId = healthy.id, url = OTHER_FEED_URL)
            val brokenFeedId = assertNotNull(repositories.feeds.findByAccountId(broken.id)).id

            val results = serviceOf(
                repositories,
                noteStore = noteStore,
                accounts = ThrowingAccountRepository(delegate = repositories.accounts, brokenId = broken.id),
            ).pollDue(now = Instant.now().plusSeconds(DUE_AFTER_SECONDS), limit = 10)

            val (failed, succeeded) = results.partition { it.feedId == brokenFeedId }
            assertEquals("処理中に例外が出た", failed.single().error)
            assertEquals(listOf("1 本目", "2 本目"), succeeded.single().postedItems.map { it.title })
        }

    @Test
    fun `取得の時期が来ていないフィードは取りに行かない`() =
        runTest {
            val repositories = FakeRepositories()
            val noteStore = FakeNoteStore()
            val account = assertNotNull(repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT))
            val service = serviceOf(repositories, noteStore = noteStore)
            service.save(accountId = account.id, url = FEED_URL)
            service.pollDue(now = Instant.now().plusSeconds(DUE_AFTER_SECONDS), limit = 10)
            val postedByFirstPoll = noteStore.added.size

            // 取得した時刻を基準にするので、その 60 秒後はまだ来ていない
            val results = service.pollDue(now = Instant.now().plusSeconds(60), limit = 10)

            assertEquals(emptyList(), results)
            assertEquals(postedByFirstPoll, noteStore.added.size)
        }

    @Test
    fun `登録した直後は取りに行かない`() =
        runTest {
            val repositories = FakeRepositories()
            val account = assertNotNull(repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT))
            val service = serviceOf(repositories)
            service.save(accountId = account.id, url = FEED_URL)

            assertEquals(emptyList(), service.pollDue(now = Instant.now(), limit = 10))
        }

    @Test
    fun `手動の取得も次の取得予定の基準にする`() =
        runTest {
            val repositories = FakeRepositories()
            val account = assertNotNull(repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT))
            val service = serviceOf(repositories)
            service.save(accountId = account.id, url = FEED_URL)
            val feed = assertNotNull(repositories.feeds.findByAccountId(account.id))
            repositories.feeds.recordFetchSuccess(
                id = feed.id,
                fetchedAt = Instant.now().minusSeconds(feed.pollIntervalSeconds + 1),
                validators = FeedFetchValidators.NONE,
            )

            service.postUnpublished(account.id)

            assertEquals(emptyList(), service.pollDue(now = Instant.now(), limit = 10))
        }

    @Test
    fun `登録の取り込み中のフィードは取りに行かない`() =
        runTest {
            val repositories = FakeRepositories()
            val noteStore = FakeNoteStore()
            val account = assertNotNull(repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT))
            val service = serviceOf(repositories, noteStore = noteStore)
            service.save(accountId = account.id, url = FEED_URL)
            repositories.feeds.clearInitialImportDone(assertNotNull(repositories.feeds.findByAccountId(account.id)).id)

            val results = service.pollDue(now = Instant.now().plusSeconds(60), limit = 10)

            assertEquals(emptyList(), results)
            assertEquals(0, noteStore.added.size)
        }

    @Test
    fun `取り込みが終わらないまま間隔を過ぎたら取り込んで投稿する`() =
        runTest {
            val repositories = FakeRepositories()
            val noteStore = FakeNoteStore()
            val account = assertNotNull(repositories.accounts.add(username = TestLocalActor.STORED_USERNAME, createdAt = CREATED_AT))
            val service = serviceOf(repositories, xmls = listOf(FEED_XML, LATEST_XML), noteStore = noteStore)
            service.save(accountId = account.id, url = FEED_URL)
            repositories.feeds.clearInitialImportDone(assertNotNull(repositories.feeds.findByAccountId(account.id)).id)

            val results = service.pollDue(now = Instant.now().plusSeconds(DUE_AFTER_SECONDS), limit = 10)

            assertEquals(listOf(null), results.map { it.error })
            assertEquals(listOf("1 本目", "2 本目", "3 本目"), results.single().postedItems.map { it.title })
            assertEquals(3, noteStore.added.size)
            assertEquals(true, assertNotNull(repositories.feeds.findByAccountId(account.id)).initialImportDone)
            assertEquals(
                listOf(FeedItemState.POSTED, FeedItemState.POSTED, FeedItemState.POSTED),
                repositories.feedItems.items().map { it.state },
            )
        }

    @Test
    fun `取得に失敗したら記録して投稿しない`() =
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
            val polledAt = Instant.now().plusSeconds(DUE_AFTER_SECONDS)

            val results = service.pollDue(now = polledAt, limit = 10)

            assertEquals(listOf("HTTP 404"), results.map { it.error })
            assertEquals(0, noteStore.added.size)
            val feed = assertNotNull(repositories.feeds.findByAccountId(account.id))
            assertEquals("HTTP 404", feed.fetch.lastError)
            assertNotNull(feed.fetch.lastFetchedAt)
            assertEquals(emptyList(), repositories.feedItems.items())
        }

    private fun serviceOf(
        repositories: FakeRepositories,
        accounts: AccountRepository = repositories.accounts,
        status: HttpStatusCode = HttpStatusCode.OK,
        xml: String = FEED_XML,
        xmls: List<String>? = null,
        statuses: List<HttpStatusCode>? = null,
        noteStore: FakeNoteStore = FakeNoteStore(),
        actorDirectory: ActorDirectory = TestLocalActor.directory,
        engine: MockEngine? = null,
        icons: FeedIcons = FakeFeedIcons(),
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
            accounts = accounts,
            feeds = repositories.feeds,
            feedItems = repositories.feedItems,
            fetcher = FeedFetchService(HttpClient(mockEngine)),
            actorDirectory = actorDirectory,
            notePublisher = NotePublisher(
                notes = noteStore,
                followers = FakeFollowerStore(),
                delivery = TestDelivery(),
                webPages = TestWebPageUrls,
            ),
            icons = icons,
        )
    }

    /**
     * 1 つのアカウントだけ引けなくする。定期ポーリングの途中で投げられた場合を作る
     */
    private class ThrowingAccountRepository(
        private val delegate: AccountRepository,
        private val brokenId: AccountId,
    ) : AccountRepository by delegate {
        override fun findById(id: AccountId): Account? {
            if (id == brokenId) error("アカウントを引けなかった")
            return delegate.findById(id)
        }
    }

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-08-16T01:02:03Z")

        // 登録時の取得が記録されるので、その間隔を過ぎるまで次の取得は来ない
        const val DUE_AFTER_SECONDS = 901L
        const val FEED_URL = "https://example.com/feed.xml"
        const val OTHER_FEED_URL = "https://example.com/other.xml"
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
        val ICON_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:webfeeds="http://webfeeds.org/rss/1.0">
              <channel>
                <title>サンプル</title>
                <link>https://example.com/</link>
                <webfeeds:icon>https://example.com/icon.png</webfeeds:icon>
                <item><title>1 本目</title><link>https://example.com/1</link></item>
              </channel>
            </rss>
        """.trimIndent()
        val CHANGED_ICON_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:webfeeds="http://webfeeds.org/rss/1.0">
              <channel>
                <title>サンプル</title>
                <link>https://example.com/</link>
                <webfeeds:icon>https://example.com/icon2.png</webfeeds:icon>
                <item><title>1 本目</title><link>https://example.com/1</link></item>
              </channel>
            </rss>
        """.trimIndent()
        val LONG_DESCRIPTION_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>サンプル</title>
                <link>https://example.com/</link>
                <description>${"あ".repeat(300)}

            最後の段落</description>
                <item><title>1 本目</title><link>https://example.com/1</link></item>
              </channel>
            </rss>
        """.trimIndent()
        val OLDEST_FIRST_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>サンプル</title>
                <link>https://example.com/</link>
                <item>
                  <title>古い記事</title>
                  <link>https://example.com/1</link>
                  <pubDate>Tue, 01 Sep 2026 00:00:00 +0000</pubDate>
                </item>
                <item>
                  <title>新しい記事</title>
                  <link>https://example.com/2</link>
                  <pubDate>Thu, 03 Sep 2026 00:00:00 +0000</pubDate>
                </item>
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
