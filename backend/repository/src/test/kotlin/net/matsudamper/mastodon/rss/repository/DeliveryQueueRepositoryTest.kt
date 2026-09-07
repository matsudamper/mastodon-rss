package net.matsudamper.mastodon.rss.repository

import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import net.matsudamper.mastodon.rss.repository.entity.DeliveryId
import net.matsudamper.mastodon.rss.repository.entity.FeedItemId
import net.matsudamper.mastodon.rss.shared.PublicNoteId

// 本物の SQLite に対して確かめる。
// 投函が 1 トランザクションで確定すること、claim が二重に取れないことがここの関心になる。
class DeliveryQueueRepositoryTest {
    private val tempDir: Path = createTempDirectory("mastodon-rss-delivery-queue-test")

    private val dbPath: Path = tempDir.resolve("test.db")

    private val now: Instant = Instant.parse("2026-08-10T00:00:00Z")

    init {
        TestSchema.applyTo(dbPath)
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun <T> withRepositories(block: (Repositories) -> T): T =
        createRepositories(DatabaseConfig(path = dbPath)).use(block)

    @Test
    fun `投函すると投稿と宛先ごとの行が入る`() {
        withRepositories { repositories ->
            val result = repositories.deliveryQueue.enqueueNote(
                notePost(publicId = "n1", inboxes = listOf(INBOX_A, INBOX_B)),
            )

            assertEquals(EnqueueNoteResult.Queued(deliveries = 2), result)
            assertNotNull(repositories.notes.find(PublicNoteId("n1")))
            assertEquals(DeliveryQueueCounts(waiting = 2, failed = 0), repositories.deliveryQueue.counts(USERNAME))

            val claimed = repositories.deliveryQueue.claim(now = now, limit = 10)
            assertEquals(listOf(INBOX_A, INBOX_B), claimed.map { it.inbox })
            assertEquals(listOf(DeliveryKind.CREATE_NOTE, DeliveryKind.CREATE_NOTE), claimed.map { it.kind })
            assertEquals(listOf(USERNAME, USERNAME), claimed.map { it.username })
            assertEquals(listOf(BODY, BODY), claimed.map { it.body })
            assertEquals(listOf(1, 1), claimed.map { it.attempts })
            assertEquals(listOf(now, now), claimed.map { it.enqueuedAt })
        }
    }

    @Test
    fun `宛先が無くても投稿は記録する`() {
        withRepositories { repositories ->
            val result = repositories.deliveryQueue.enqueueNote(notePost(publicId = "n1", inboxes = emptyList()))

            assertEquals(EnqueueNoteResult.Queued(deliveries = 0), result)
            assertNotNull(repositories.notes.find(PublicNoteId("n1")))
            assertEquals(emptyList(), repositories.deliveryQueue.claim(now = now, limit = 10))
        }
    }

    @Test
    fun `記事から投函すると記事が投稿済みになる`() {
        withRepositories { repositories ->
            val item = repositories.addPendingItem()

            val result = repositories.deliveryQueue.enqueueNote(
                notePost(publicId = "n1", inboxes = listOf(INBOX_A), feedItemId = item.id),
            )

            assertEquals(EnqueueNoteResult.Queued(deliveries = 1), result)
            val posted = assertNotNull(repositories.feedItems.find(item.id))
            assertEquals(FeedItemState.POSTED, posted.state)
            assertEquals(PublicNoteId("n1"), posted.noteId)
            assertEquals(now, posted.postedAt)
        }
    }

    @Test
    fun `同じ記事への投函が 2 回走っても投稿とキューは 1 回しかできない`() {
        withRepositories { repositories ->
            val item = repositories.addPendingItem()
            repositories.deliveryQueue.enqueueNote(notePost(publicId = "n1", inboxes = listOf(INBOX_A), feedItemId = item.id))

            val second = repositories.deliveryQueue.enqueueNote(
                notePost(publicId = "n2", inboxes = listOf(INBOX_A), feedItemId = item.id),
            )

            assertEquals(EnqueueNoteResult.FeedItemNotPending, second)
            assertNull(repositories.notes.find(PublicNoteId("n2")))
            assertEquals(1L, repositories.notes.count(USERNAME))
            assertEquals(DeliveryQueueCounts(waiting = 1, failed = 0), repositories.deliveryQueue.counts(USERNAME))
            assertEquals(PublicNoteId("n1"), assertNotNull(repositories.feedItems.find(item.id)).noteId)
        }
    }

    @Test
    fun `無い記事から投函しても何も書かない`() {
        withRepositories { repositories ->
            val result = repositories.deliveryQueue.enqueueNote(
                notePost(publicId = "n1", inboxes = listOf(INBOX_A), feedItemId = FeedItemId(999)),
            )

            assertEquals(EnqueueNoteResult.FeedItemNotPending, result)
            assertNull(repositories.notes.find(PublicNoteId("n1")))
            assertEquals(DeliveryQueueCounts(waiting = 0, failed = 0), repositories.deliveryQueue.counts(USERNAME))
        }
    }

    @Test
    fun `途中で失敗したらどれも残らない`() {
        withRepositories { repositories ->
            val item = repositories.addPendingItem()
            repositories.deliveryQueue.enqueueNote(notePost(publicId = "dup", inboxes = listOf(INBOX_A)))
            val another = repositories.addPendingItem(itemKey = "another")

            // 公開 id が重複するので notes への INSERT で落ちる。先に更新した記事が巻き戻ること
            runCatching {
                repositories.deliveryQueue.enqueueNote(
                    notePost(publicId = "dup", inboxes = listOf(INBOX_B), feedItemId = another.id),
                )
            }.also { assertEquals(true, it.isFailure) }

            assertEquals(FeedItemState.PENDING, assertNotNull(repositories.feedItems.find(another.id)).state)
            assertEquals(FeedItemState.PENDING, assertNotNull(repositories.feedItems.find(item.id)).state)
            assertEquals(DeliveryQueueCounts(waiting = 1, failed = 0), repositories.deliveryQueue.counts(USERNAME))
        }
    }

    @Test
    fun `claim は送る時刻を過ぎた pending だけを古い順に delivering にする`() {
        withRepositories { repositories ->
            repositories.deliveryQueue.enqueueNote(notePost(publicId = "n1", inboxes = listOf(INBOX_A), enqueuedAt = now.plusSeconds(60)))
            repositories.deliveryQueue.enqueueNote(notePost(publicId = "n2", inboxes = listOf(INBOX_B), enqueuedAt = now))

            val first = repositories.deliveryQueue.claim(now = now, limit = 10)
            assertEquals(listOf(INBOX_B), first.map { it.inbox })
            assertEquals(listOf(1), first.map { it.attempts })

            // delivering になった行は取り直せない
            assertEquals(emptyList(), repositories.deliveryQueue.claim(now = now, limit = 10))

            val later = repositories.deliveryQueue.claim(now = now.plusSeconds(60), limit = 10)
            assertEquals(listOf(INBOX_A), later.map { it.inbox })
        }
    }

    @Test
    fun `claim は limit までしか取らない`() {
        withRepositories { repositories ->
            repositories.deliveryQueue.enqueueNote(notePost(publicId = "n1", inboxes = listOf(INBOX_A, INBOX_B, INBOX_C)))

            assertEquals(2, repositories.deliveryQueue.claim(now = now, limit = 2).size)
            assertEquals(1, repositories.deliveryQueue.claim(now = now, limit = 2).size)
            assertEquals(0, repositories.deliveryQueue.claim(now = now, limit = 2).size)
        }
    }

    @Test
    fun `起動時に delivering を pending に戻す`() {
        withRepositories { repositories ->
            repositories.deliveryQueue.enqueueNote(notePost(publicId = "n1", inboxes = listOf(INBOX_A)))
            repositories.deliveryQueue.claim(now = now, limit = 10)

            assertEquals(1, repositories.deliveryQueue.recoverDelivering())
            assertEquals(0, repositories.deliveryQueue.recoverDelivering())

            // 戻した行は attempts を引き継いだまま、もう一度 claim できる
            val claimed = repositories.deliveryQueue.claim(now = now, limit = 10)
            assertEquals(listOf(2), claimed.map { it.attempts })
        }
    }

    @Test
    fun `成功した行は消える`() {
        withRepositories { repositories ->
            repositories.deliveryQueue.enqueueNote(notePost(publicId = "n1", inboxes = listOf(INBOX_A)))
            val claimed = repositories.deliveryQueue.claim(now = now, limit = 10).single()

            repositories.deliveryQueue.markDelivered(claimed.id)

            assertEquals(DeliveryQueueCounts(waiting = 0, failed = 0), repositories.deliveryQueue.counts(USERNAME))
            assertEquals(0, repositories.deliveryQueue.recoverDelivering())
        }
    }

    @Test
    fun `送り直しは指定した時刻まで claim されず一覧に出る`() {
        withRepositories { repositories ->
            repositories.deliveryQueue.enqueueNote(notePost(publicId = "n1", inboxes = listOf(INBOX_A)))
            val claimed = repositories.deliveryQueue.claim(now = now, limit = 10).single()
            val retryAt = now.plusSeconds(30)

            repositories.deliveryQueue.scheduleRetry(claimed.id, nextAttemptAt = retryAt, error = "HTTP 503")

            assertEquals(emptyList(), repositories.deliveryQueue.claim(now = retryAt.minusSeconds(1), limit = 10))
            assertEquals(DeliveryQueueCounts(waiting = 1, failed = 0), repositories.deliveryQueue.counts(USERNAME))

            val retrying = repositories.deliveryQueue.listRetrying(USERNAME, after = null, limit = 10).single()
            assertEquals(INBOX_A, retrying.inbox)
            assertEquals(1, retrying.attempts)
            assertEquals(retryAt, retrying.nextAttemptAt)
            assertEquals("HTTP 503", retrying.lastError)

            val again = repositories.deliveryQueue.claim(now = retryAt, limit = 10).single()
            assertEquals(2, again.attempts)
            assertEquals(BODY, again.body)
        }
    }

    @Test
    fun `まだ一度も送っていない行は送り直し待ちに出ない`() {
        withRepositories { repositories ->
            repositories.deliveryQueue.enqueueNote(notePost(publicId = "n1", inboxes = listOf(INBOX_A)))

            assertEquals(emptyList(), repositories.deliveryQueue.listRetrying(USERNAME, after = null, limit = 10))
        }
    }

    @Test
    fun `諦めた行は body と次の時刻が消えて failed に数えられる`() {
        withRepositories { repositories ->
            repositories.deliveryQueue.enqueueNote(notePost(publicId = "n1", inboxes = listOf(INBOX_A)))
            val claimed = repositories.deliveryQueue.claim(now = now, limit = 10).single()

            repositories.deliveryQueue.giveUp(claimed.id, error = "30 日を過ぎた")

            assertEquals(DeliveryQueueCounts(waiting = 0, failed = 1), repositories.deliveryQueue.counts(USERNAME))
            assertEquals(emptyList(), repositories.deliveryQueue.claim(now = now.plusSeconds(3600), limit = 10))
            assertEquals(emptyList(), repositories.deliveryQueue.listRetrying(USERNAME, after = null, limit = 10))

            val failed = repositories.deliveryQueue.listFailed(USERNAME, afterId = null, limit = 10).single()
            assertEquals(INBOX_A, failed.inbox)
            assertEquals(1, failed.attempts)
            assertEquals("30 日を過ぎた", failed.lastError)
            assertEquals(claimed.id, failed.id)

            // body が NULL になっていること。カラムは外に出していないので、claim できない形で確かめる
            assertEquals(0, repositories.deliveryQueue.recoverDelivering())
        }
    }

    @Test
    fun `送り直し待ちの一覧は位置から続きを返す`() {
        withRepositories { repositories ->
            repositories.deliveryQueue.enqueueNote(notePost(publicId = "n1", inboxes = listOf(INBOX_A, INBOX_B, INBOX_C)))
            repositories.deliveryQueue.claim(now = now, limit = 10).forEachIndexed { index, claimed ->
                repositories.deliveryQueue.scheduleRetry(claimed.id, nextAttemptAt = now.plusSeconds(10L * (3 - index)), error = "e")
            }

            val firstPage = repositories.deliveryQueue.listRetrying(USERNAME, after = null, limit = 2)
            assertEquals(listOf(INBOX_C, INBOX_B), firstPage.map { it.inbox })

            val secondPage = repositories.deliveryQueue.listRetrying(USERNAME, after = firstPage.last().position, limit = 2)
            assertEquals(listOf(INBOX_A), secondPage.map { it.inbox })
        }
    }

    @Test
    fun `諦めた一覧は新しい順で id から続きを返す`() {
        withRepositories { repositories ->
            repositories.deliveryQueue.enqueueNote(notePost(publicId = "n1", inboxes = listOf(INBOX_A, INBOX_B, INBOX_C)))
            repositories.deliveryQueue.claim(now = now, limit = 10).forEach { claimed ->
                repositories.deliveryQueue.giveUp(claimed.id, error = "e")
            }

            val firstPage = repositories.deliveryQueue.listFailed(USERNAME, afterId = null, limit = 2)
            assertEquals(listOf(INBOX_C, INBOX_B), firstPage.map { it.inbox })

            val secondPage = repositories.deliveryQueue.listFailed(USERNAME, afterId = firstPage.last().id, limit = 2)
            assertEquals(listOf(INBOX_A), secondPage.map { it.inbox })
        }
    }

    @Test
    fun `件数と一覧はアカウントごとに分かれる`() {
        withRepositories { repositories ->
            repositories.deliveryQueue.enqueueNote(notePost(publicId = "n1", inboxes = listOf(INBOX_A)))
            repositories.deliveryQueue.enqueueNote(notePost(publicId = "n2", username = "other", inboxes = listOf(INBOX_B)))

            assertEquals(DeliveryQueueCounts(waiting = 1, failed = 0), repositories.deliveryQueue.counts(USERNAME))
            assertEquals(DeliveryQueueCounts(waiting = 1, failed = 0), repositories.deliveryQueue.counts("other"))
            assertEquals(DeliveryQueueCounts(waiting = 0, failed = 0), repositories.deliveryQueue.counts("nobody"))
        }
    }

    @Test
    fun `アカウントの配信をまとめて消せる`() {
        withRepositories { repositories ->
            repositories.deliveryQueue.enqueueNote(notePost(publicId = "n1", inboxes = listOf(INBOX_A, INBOX_B)))
            repositories.deliveryQueue.enqueueNote(notePost(publicId = "n2", username = "other", inboxes = listOf(INBOX_C)))

            assertEquals(2, repositories.deliveryQueue.deleteByUsername(USERNAME))

            assertEquals(DeliveryQueueCounts(waiting = 0, failed = 0), repositories.deliveryQueue.counts(USERNAME))
            assertEquals(listOf(INBOX_C), repositories.deliveryQueue.claim(now = now, limit = 10).map { it.inbox })
        }
    }

    @Test
    fun `投稿を消すとまだ配っていない配信も消える`() {
        withRepositories { repositories ->
            repositories.deliveryQueue.enqueueNote(notePost(publicId = "n1", inboxes = listOf(INBOX_A, INBOX_B)))
            repositories.deliveryQueue.enqueueNote(notePost(publicId = "n2", inboxes = listOf(INBOX_C)))
            // 送信中の行も消える。送り終わった後の markDelivered は何も消さないだけ
            repositories.deliveryQueue.claim(now = now, limit = 1)

            repositories.notes.delete(PublicNoteId("n1"))

            assertEquals(DeliveryQueueCounts(waiting = 1, failed = 0), repositories.deliveryQueue.counts(USERNAME))
            assertEquals(listOf(INBOX_C), repositories.deliveryQueue.claim(now = now, limit = 10).map { it.inbox })
        }
    }

    @Test
    fun `exists は消えた行に false を返す`() {
        withRepositories { repositories ->
            repositories.deliveryQueue.enqueueNote(notePost(publicId = "n1", inboxes = listOf(INBOX_A)))
            val claimed = repositories.deliveryQueue.claim(now = now, limit = 10).single()

            assertEquals(true, repositories.deliveryQueue.exists(claimed.id))

            repositories.notes.delete(PublicNoteId("n1"))

            assertEquals(false, repositories.deliveryQueue.exists(claimed.id))
        }
    }

    @Test
    fun `一覧の id は claim した行と同じ`() {
        withRepositories { repositories ->
            repositories.deliveryQueue.enqueueNote(notePost(publicId = "n1", inboxes = listOf(INBOX_A)))
            val claimed = repositories.deliveryQueue.claim(now = now, limit = 10).single()
            repositories.deliveryQueue.scheduleRetry(claimed.id, nextAttemptAt = now, error = "e")

            val retrying = repositories.deliveryQueue.listRetrying(USERNAME, after = null, limit = 10).single()

            assertIs<DeliveryId>(retrying.id)
            assertEquals(claimed.id, retrying.id)
        }
    }

    private fun notePost(
        publicId: String,
        inboxes: List<String>,
        username: String = USERNAME,
        enqueuedAt: Instant = now,
        feedItemId: FeedItemId? = null,
    ): NotePost = NotePost(
        note = NewNote(
            username = username,
            publicId = PublicNoteId(publicId),
            contentHtml = "<p>$publicId</p>",
            publishedAt = enqueuedAt,
        ),
        body = BODY,
        inboxes = inboxes,
        enqueuedAt = enqueuedAt,
        feedItemId = feedItemId,
    )

    private fun Repositories.addPendingItem(itemKey: String = "item"): FeedItem {
        val account = accounts.findByUsername(USERNAME) ?: assertNotNull(accounts.add(USERNAME, now))
        val feed = feeds.findByAccountId(account.id) ?: assertNotNull(
            feeds.add(
                NewFeed(
                    accountId = account.id,
                    url = "https://example.com/feed.xml",
                    title = null,
                    siteUrl = null,
                    format = null,
                    pollIntervalSeconds = 900,
                ),
            ),
        )
        return assertNotNull(
            feedItems.add(
                NewFeedItem(
                    feedId = feed.id,
                    itemKey = itemKey,
                    title = "題名",
                    link = "https://example.com/1",
                    contentHtml = "<p>本文</p>",
                    publishedAt = now,
                    importedAt = now,
                    state = FeedItemState.PENDING,
                ),
            ),
        )
    }

    private companion object {
        const val USERNAME = "admin"
        const val BODY = """{"type":"Create"}"""
        const val INBOX_A = "https://a.example/inbox"
        const val INBOX_B = "https://b.example/inbox"
        const val INBOX_C = "https://c.example/inbox"
    }
}
