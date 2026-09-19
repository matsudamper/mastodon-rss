package net.matsudamper.mastodon.rss.delivery

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import net.matsudamper.mastodon.rss.FakeDeliveryQueueRepository
import net.matsudamper.mastodon.rss.FakeNoteRepository
import net.matsudamper.mastodon.rss.FakeRepositories
import net.matsudamper.mastodon.rss.TestLocalActor
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.repository.ClaimedDelivery
import net.matsudamper.mastodon.rss.repository.DeliveryQueueRepository
import net.matsudamper.mastodon.rss.repository.NewNote
import net.matsudamper.mastodon.rss.repository.NotePost
import net.matsudamper.mastodon.rss.repository.entity.DeliveryId
import net.matsudamper.mastodon.rss.shared.PublicNoteId

// キューの行を拾って送るところ。
// 同じホストは同時に送らない、違うホストは並列、1 件の失敗で止まらない、キャンセルは失敗として残さない。
class DeliveryWorkerTest {
    private val now: Instant = Instant.parse("2026-08-10T00:00:00Z")

    @Test
    fun `送れた行は消える`() = runTest {
        val repositories = FakeRepositories()
        val delivery = RecordingDelivery()
        repositories.enqueue(inboxes = listOf("https://a.example/inbox", "https://b.example/inbox"))

        runWorker(repositories.deliveryQueue, delivery)

        assertEquals(emptyList(), repositories.deliveryQueue.rows())
        assertEquals(setOf("https://a.example/inbox", "https://b.example/inbox"), delivery.delivered.toSet())
        assertEquals(TestLocalActor.USERNAME, assertNotNull(delivery.senders.firstOrNull()).username)
    }

    @Test
    fun `失敗した行は送り直しを待つ`() = runTest {
        val repositories = FakeRepositories()
        val delivery = RecordingDelivery(failing = setOf("https://a.example/inbox"))
        repositories.enqueue(inboxes = listOf("https://a.example/inbox"))

        runWorker(repositories.deliveryQueue, delivery)

        val row = repositories.deliveryQueue.rows().single()
        assertEquals(FakeDeliveryQueueRepository.State.PENDING, row.state)
        assertEquals(1, row.attempts)
        assertEquals(now.plusSeconds(30), row.nextAttemptAt)
        assertEquals("届かない", row.lastError)
    }

    @Test
    fun `送り直しの時刻まで待ってからもう一度送る`() = runTest {
        val repositories = FakeRepositories()
        val delivery = RecordingDelivery(failing = setOf("https://a.example/inbox"), failTimes = 1)
        repositories.enqueue(inboxes = listOf("https://a.example/inbox"))
        var current = now
        val worker = DeliveryWorker(
            queue = repositories.deliveryQueue,
            delivery = delivery,
            directory = TestLocalActor.directory,
            idleInterval = IDLE,
            clock = { current },
            retryPolicy = TEST_RETRY_POLICY,
            claimLimit = 8,
        )

        val job = worker.start(this)
        advanceTimeBy(IDLE * 2)
        assertEquals(1, repositories.deliveryQueue.rows().size)

        // 時刻を送り直しの時刻まで進める。待ちの間は claim されない
        current = now.plusSeconds(29)
        advanceTimeBy(IDLE * 2)
        assertEquals(1, delivery.attempts)

        current = now.plusSeconds(30)
        advanceTimeBy(IDLE * 2)
        job.cancelAndJoin()

        assertEquals(2, delivery.attempts)
        assertEquals(emptyList(), repositories.deliveryQueue.rows())
    }

    @Test
    fun `次に送る時刻が期限を過ぎる失敗は諦める`() = runTest {
        val repositories = FakeRepositories()
        val delivery = RecordingDelivery(failing = setOf("https://a.example/inbox"))
        repositories.enqueue(inboxes = listOf("https://a.example/inbox"))

        // 期限内に送って失敗するが、次の時刻は期限を過ぎる
        runWorker(
            repositories.deliveryQueue,
            delivery,
            retryPolicy = DeliveryRetryPolicy(initialInterval = 2.hours, maxInterval = 24.hours, giveUpAfter = 1.hours),
        )

        assertEquals(1, delivery.attempts)
        val row = repositories.deliveryQueue.rows().single()
        assertEquals(FakeDeliveryQueueRepository.State.FAILED, row.state)
        assertEquals(null, row.nextAttemptAt)
        assertEquals(null, row.body)
    }

    @Test
    fun `相手が受け取らないと決めた失敗は送り直さずに諦める`() = runTest {
        val repositories = FakeRepositories()
        val delivery = RecordingDelivery(failing = setOf("https://a.example/inbox"), retryable = false)
        repositories.enqueue(inboxes = listOf("https://a.example/inbox"))

        runWorker(repositories.deliveryQueue, delivery)

        // 消えた inbox に 30 日送り続けない
        assertEquals(1, delivery.attempts)
        val row = repositories.deliveryQueue.rows().single()
        assertEquals(FakeDeliveryQueueRepository.State.FAILED, row.state)
        assertEquals(null, row.nextAttemptAt)
        assertEquals("届かない", row.lastError)
    }

    @Test
    fun `投函から 30 日を過ぎた行は送らずに諦める`() = runTest {
        val repositories = FakeRepositories()
        val delivery = RecordingDelivery()
        repositories.enqueue(inboxes = listOf("https://a.example/inbox"))

        runWorker(repositories.deliveryQueue, delivery, clock = { now.plusSeconds(31L * 24 * 60 * 60) })

        assertEquals(FakeDeliveryQueueRepository.State.FAILED, repositories.deliveryQueue.rows().single().state)
        // 止まっていた間に期限を過ぎた投稿を、再起動後に突然届けない
        assertEquals(emptyList(), delivery.delivered)
        assertEquals(0, delivery.attempts)
    }

    @Test
    fun `起動時の復旧が失敗してもワーカーは止まらない`() = runTest {
        val repositories = FakeRepositories()
        val delivery = RecordingDelivery()
        repositories.enqueue(inboxes = listOf("https://a.example/inbox"))
        repositories.deliveryQueue.claim(now = now, limit = 10)
        val queue = FailingRecoveryOnce(repositories.deliveryQueue)

        val worker = DeliveryWorker(
            queue = queue,
            delivery = delivery,
            directory = TestLocalActor.directory,
            idleInterval = IDLE,
            clock = { now },
            retryPolicy = TEST_RETRY_POLICY,
            claimLimit = 8,
        )
        val job = worker.start(this)
        advanceTimeBy(IDLE * 10)
        job.cancelAndJoin()

        assertEquals(listOf("https://a.example/inbox"), delivery.delivered)
        assertEquals(emptyList(), repositories.deliveryQueue.rows())
    }

    @Test
    fun `結果の記録に失敗した行は送り直し待ちに戻り 再起動しなくても送り直される`() = runTest {
        val repositories = FakeRepositories()
        val delivery = RecordingDelivery()
        repositories.enqueue(inboxes = listOf("https://a.example/inbox"))
        val queue = FailingMarkDeliveredOnce(repositories.deliveryQueue)
        var current = now
        val worker = DeliveryWorker(
            queue = queue,
            delivery = delivery,
            directory = TestLocalActor.directory,
            idleInterval = IDLE,
            clock = { current },
            retryPolicy = TEST_RETRY_POLICY,
            claimLimit = 8,
        )

        val job = worker.start(this)
        advanceTimeBy(IDLE * 2)
        val row = repositories.deliveryQueue.rows().single()
        assertEquals(FakeDeliveryQueueRepository.State.PENDING, row.state)
        assertTrue(assertNotNull(row.lastError).contains("記録できなかった"))

        current = now.plusSeconds(30)
        advanceTimeBy(IDLE * 2)
        job.cancelAndJoin()

        assertEquals(2, delivery.attempts)
        assertEquals(emptyList(), repositories.deliveryQueue.rows())
    }

    @Test
    fun `claim した後に投稿が消えた行は送らない`() = runTest {
        val repositories = FakeRepositories()
        val delivery = RecordingDelivery()
        repositories.enqueue(inboxes = listOf("https://a.example/inbox"))
        val queue = DeletingNotesOnClaim(repositories.deliveryQueue, repositories.notes)
        val worker = DeliveryWorker(
            queue = queue,
            delivery = delivery,
            directory = TestLocalActor.directory,
            idleInterval = IDLE,
            clock = { now },
            retryPolicy = TEST_RETRY_POLICY,
            claimLimit = 8,
        )

        val job = worker.start(this)
        advanceTimeBy(IDLE * 5)
        job.cancelAndJoin()

        assertEquals(emptyList(), delivery.delivered)
        assertEquals(emptyList(), repositories.deliveryQueue.rows())
    }

    @Test
    fun `送れないホスト宛が溜まっていても 他のホスト宛を待たせない`() = runTest {
        val repositories = FakeRepositories()
        val delivery = RecordingDelivery(latency = 100.milliseconds)
        repositories.enqueue(inboxes = (1..8).map { "https://a.example/users/$it/inbox" })
        repositories.enqueue(inboxes = listOf("https://b.example/inbox"))
        val worker = DeliveryWorker(
            queue = repositories.deliveryQueue,
            delivery = delivery,
            directory = TestLocalActor.directory,
            idleInterval = IDLE,
            claimLimit = 8,
            clock = { now },
            retryPolicy = TEST_RETRY_POLICY,
        )

        val job = worker.start(this)
        // 溜まっているホストの 1 件目と一緒に送り始める
        advanceTimeBy(100.milliseconds + IDLE)
        job.cancelAndJoin()

        assertEquals(
            listOf("https://a.example/users/1/inbox", "https://b.example/inbox"),
            delivery.delivered.sorted(),
        )
    }

    @Test
    fun `起動時に delivering を pending に戻して送る`() = runTest {
        val repositories = FakeRepositories()
        val delivery = RecordingDelivery()
        repositories.enqueue(inboxes = listOf("https://a.example/inbox"))
        // 前回の起動が送信中に落ちた状態を作る
        repositories.deliveryQueue.claim(now = now, limit = 10)

        runWorker(repositories.deliveryQueue, delivery)

        assertEquals(listOf("https://a.example/inbox"), delivery.delivered)
        assertEquals(emptyList(), repositories.deliveryQueue.rows())
    }

    @Test
    fun `同じホスト宛は同時に送らず 異なるホスト宛は並列に送る`() = runTest {
        val repositories = FakeRepositories()
        val delivery = RecordingDelivery(latency = 100.milliseconds)
        repositories.enqueue(
            inboxes = listOf(
                "https://a.example/users/1/inbox",
                "https://a.example/users/2/inbox",
                "https://a.example/users/3/inbox",
                "https://b.example/inbox",
                "https://c.example/inbox",
            ),
        )

        runWorker(repositories.deliveryQueue, delivery)

        assertEquals(1, delivery.maxConcurrentByHost.getValue("a.example"))
        // 3 ホストが同時に送っている瞬間がある
        assertEquals(3, delivery.maxConcurrent)
        assertEquals(5, delivery.delivered.size)
    }

    @Test
    fun `全体の同時実行数は claim の上限を超えない`() = runTest {
        val repositories = FakeRepositories()
        val delivery = RecordingDelivery(latency = 100.milliseconds)
        repositories.enqueue(inboxes = (1..20).map { "https://host$it.example/inbox" })

        runWorker(repositories.deliveryQueue, delivery, claimLimit = 8)

        assertEquals(8, delivery.maxConcurrent)
        assertEquals(20, delivery.delivered.size)
    }

    @Test
    fun `配送 1 件で例外が出てもその行の失敗として扱い 残りを送る`() = runTest {
        val repositories = FakeRepositories()
        val delivery = RecordingDelivery(throwing = setOf("https://a.example/inbox"))
        repositories.enqueue(inboxes = listOf("https://a.example/inbox", "https://b.example/inbox"))

        runWorker(repositories.deliveryQueue, delivery)

        val remaining = repositories.deliveryQueue.rows().single()
        assertEquals("https://a.example/inbox", remaining.inbox)
        assertEquals(FakeDeliveryQueueRepository.State.PENDING, remaining.state)
        assertTrue(assertNotNull(remaining.lastError).contains("壊れた"))
        assertEquals(listOf("https://b.example/inbox"), delivery.delivered)
    }

    @Test
    fun `アカウントが無い行は諦める`() = runTest {
        val repositories = FakeRepositories()
        val delivery = RecordingDelivery()
        repositories.enqueue(username = "nobody", inboxes = listOf("https://a.example/inbox"))

        runWorker(repositories.deliveryQueue, delivery)

        assertEquals(FakeDeliveryQueueRepository.State.FAILED, repositories.deliveryQueue.rows().single().state)
        assertEquals(emptyList(), delivery.delivered)
    }

    @Test
    fun `キャンセルは配送失敗として記録せず 送信中の行を delivering のまま残す`() = runTest {
        val repositories = FakeRepositories()
        val delivery = RecordingDelivery(latency = 10.seconds)
        repositories.enqueue(inboxes = listOf("https://a.example/inbox"))
        val worker = DeliveryWorker(
            queue = repositories.deliveryQueue,
            delivery = delivery,
            directory = TestLocalActor.directory,
            idleInterval = IDLE,
            clock = { now },
            retryPolicy = TEST_RETRY_POLICY,
            claimLimit = 8,
        )

        val job = worker.start(this)
        advanceTimeBy(1.seconds)
        job.cancelAndJoin()

        val row = repositories.deliveryQueue.rows().single()
        assertEquals(FakeDeliveryQueueRepository.State.DELIVERING, row.state)
        assertEquals(null, row.lastError)
        assertEquals(emptyList(), delivery.delivered)
        // 止めた後は 1 件も送りに行かない
        advanceTimeBy(1.minutes())
        assertEquals(1, delivery.attempts)
    }

    /**
     * キューが空になるまで回してから止める
     */
    private suspend fun TestScope.runWorker(
        queue: FakeDeliveryQueueRepository,
        delivery: RecordingDelivery,
        claimLimit: Int = 8,
        clock: () -> Instant = { now },
        retryPolicy: DeliveryRetryPolicy = TEST_RETRY_POLICY,
    ) {
        val worker = DeliveryWorker(
            queue = queue,
            delivery = delivery,
            directory = TestLocalActor.directory,
            retryPolicy = retryPolicy,
            idleInterval = IDLE,
            claimLimit = claimLimit,
            clock = clock,
        )
        val job: Job = worker.start(this)
        // 送るのに掛かる時間を進めて、空振りの待ちまで回してから止める
        advanceTimeBy(IDLE * 10 + 10.seconds)
        job.cancelAndJoin()
    }

    private fun FakeRepositories.enqueue(
        inboxes: List<String>,
        username: String = TestLocalActor.USERNAME,
    ) {
        deliveryQueue.enqueueNote(
            NotePost(
                note = NewNote(
                    username = username,
                    publicId = PublicNoteId("note-${deliveryQueue.rows().size}"),
                    contentHtml = "<p>本文</p>",
                    publishedAt = now,
                ),
                body = """{"type":"Create"}""",
                inboxes = inboxes,
                enqueuedAt = now,
                feedItemId = null,
            ),
        )
    }

    private fun Int.minutes() = (this * 60).seconds

    /**
     * 起動時の復旧だけ 1 回失敗させる
     */
    private class FailingRecoveryOnce(
        private val delegate: FakeDeliveryQueueRepository,
    ) : DeliveryQueueRepository by delegate {
        private var failed = false

        override fun recoverDelivering(): Int {
            if (!failed) {
                failed = true
                throw IllegalStateException("DB がロックされている")
            }
            return delegate.recoverDelivering()
        }
    }

    /**
     * claim してから送り始めるまでの間に投稿が消された状態を作る
     */
    private class DeletingNotesOnClaim(
        private val delegate: FakeDeliveryQueueRepository,
        private val notes: FakeNoteRepository,
    ) : DeliveryQueueRepository by delegate {
        override fun claim(
            now: Instant,
            limit: Int,
        ): List<ClaimedDelivery> {
            val claimed = delegate.claim(now = now, limit = limit)
            notes.all().forEach { notes.delete(it.publicId) }
            return claimed
        }
    }

    /**
     * 送れた記録だけ 1 回失敗させる
     */
    private class FailingMarkDeliveredOnce(
        private val delegate: FakeDeliveryQueueRepository,
    ) : DeliveryQueueRepository by delegate {
        private var failed = false

        override fun markDelivered(id: DeliveryId) {
            if (!failed) {
                failed = true
                throw IllegalStateException("DB がロックされている")
            }
            delegate.markDelivered(id)
        }
    }

    /**
     * 送信の差し替え。同時に何件送っているかをホストごとに数える
     *
     * @param failing 失敗を返す宛先
     * @param failTimes 失敗を返す回数。0 なら毎回
     * @param throwing 例外を投げる宛先
     * @param latency 1 件に掛かる時間。並列の確認に使う
     * @param retryable 失敗を送り直せるものとして返すか
     */
    private class RecordingDelivery(
        private val failing: Set<String> = emptySet(),
        private val failTimes: Int = 0,
        private val throwing: Set<String> = emptySet(),
        private val latency: kotlin.time.Duration = kotlin.time.Duration.ZERO,
        private val retryable: Boolean = true,
    ) : ActivityDelivery {
        val delivered = mutableListOf<String>()
        val senders = mutableListOf<ActorUrls>()
        var attempts = 0
            private set
        var maxConcurrent = 0
            private set
        val maxConcurrentByHost = mutableMapOf<String, Int>()

        private var concurrent = 0
        private val concurrentByHost = mutableMapOf<String, Int>()
        private var failed = 0

        override suspend fun deliver(
            inbox: String,
            sender: ActorUrls,
            body: ByteArray,
        ): DeliveryResult {
            attempts++
            val host = java.net.URI(inbox).host
            concurrent++
            concurrentByHost[host] = (concurrentByHost[host] ?: 0) + 1
            maxConcurrent = maxOf(maxConcurrent, concurrent)
            maxConcurrentByHost[host] = maxOf(maxConcurrentByHost[host] ?: 0, concurrentByHost.getValue(host))
            try {
                delay(latency)
                if (inbox in throwing) throw IllegalStateException("壊れた宛先")
                if (inbox in failing && (failTimes == 0 || failed < failTimes)) {
                    failed++
                    return DeliveryResult.Failed(reason = "届かない", retryable = retryable)
                }
                delivered += inbox
                senders += sender
                return DeliveryResult.Delivered
            } finally {
                concurrent--
                concurrentByHost[host] = concurrentByHost.getValue(host) - 1
            }
        }

        override fun close() {
        }
    }

    private companion object {
        val IDLE = 100.milliseconds

        // 本番と同じ間隔。テストは時刻を自分で進めるので、実際に待つことはない
        val TEST_RETRY_POLICY = DeliveryRetryPolicy(
            initialInterval = 30.seconds,
            maxInterval = 24.hours,
            giveUpAfter = 30.days,
        )
    }
}
