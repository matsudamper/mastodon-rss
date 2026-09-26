package net.matsudamper.mastodon.rss.delivery

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import net.matsudamper.mastodon.rss.FakeDeliveryQueueRepository
import net.matsudamper.mastodon.rss.FakeDomainBlockRepository
import net.matsudamper.mastodon.rss.FakeNoteRepository
import net.matsudamper.mastodon.rss.FakeNoteStore
import net.matsudamper.mastodon.rss.FakeRepositories
import net.matsudamper.mastodon.rss.FakeStoredActorNames
import net.matsudamper.mastodon.rss.TestLocalActor
import net.matsudamper.mastodon.rss.TestWebPageUrls
import net.matsudamper.mastodon.rss.actor.ActorDirectory
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.entity.PublicNoteId as MastodonPublicNoteId
import net.matsudamper.mastodon.rss.logic.DomainBlockService
import net.matsudamper.mastodon.rss.note.FollowBackfillPublisher
import net.matsudamper.mastodon.rss.note.StoredNote
import net.matsudamper.mastodon.rss.repository.ClaimedDelivery
import net.matsudamper.mastodon.rss.repository.DeliveredOutcome
import net.matsudamper.mastodon.rss.repository.DeliveryQueueRepository
import net.matsudamper.mastodon.rss.repository.DomainBlockReason
import net.matsudamper.mastodon.rss.repository.DomainBlockRepository
import net.matsudamper.mastodon.rss.repository.IncomingFollow
import net.matsudamper.mastodon.rss.repository.NewNote
import net.matsudamper.mastodon.rss.repository.NewRemoteActor
import net.matsudamper.mastodon.rss.repository.NotePost
import net.matsudamper.mastodon.rss.repository.RemoteActorProfile
import net.matsudamper.mastodon.rss.repository.entity.DeliveryId
import net.matsudamper.mastodon.rss.shared.PublicNoteId

// キューの行を拾って送るところ。
// 並列に送る、1 件の失敗で止まらない、キャンセルは失敗として残さない。
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
    fun `Accept が届いたらフォロワーとして数え 過去の投稿を配る`() = runTest {
        val repositories = FakeRepositories()
        val delivery = RecordingDelivery()
        val notes = FakeNoteStore().apply {
            add(
                StoredNote(
                    publicId = MastodonPublicNoteId("note-1"),
                    username = TestLocalActor.USERNAME,
                    contentHtml = "<p>フォローより前</p>",
                    publishedAt = now.minusSeconds(60),
                ),
            )
        }
        repositories.followers.record(incomingFollow())

        runWorker(repositories.deliveryQueue, delivery, notes = notes)

        // Accept が届いて初めて成立する。数え始めるのも配り始めるのもここから
        assertEquals(1, repositories.followers.count(TestLocalActor.USERNAME))
        assertEquals(listOf(FOLLOWER_INBOX, FOLLOWER_INBOX), delivery.delivered)
        assertEquals(emptyList(), repositories.deliveryQueue.rows())
    }

    @Test
    fun `Accept が届かなければフォロワーに数えず 過去の投稿も配らない`() = runTest {
        val repositories = FakeRepositories()
        val delivery = RecordingDelivery(failing = setOf(FOLLOWER_INBOX))
        repositories.followers.record(incomingFollow())

        runWorker(repositories.deliveryQueue, delivery)

        assertEquals(0, repositories.followers.count(TestLocalActor.USERNAME))
        assertEquals(emptyList(), delivery.delivered)
        // 送り直しを待つ。1 回届かなかっただけで保留のまま残さない
        assertEquals(FakeDeliveryQueueRepository.State.PENDING, repositories.deliveryQueue.rows().single().state)
    }

    @Test
    fun `消したアカウントとして署名するのは Delete だけ`() = runTest {
        val repositories = FakeRepositories()
        val delivery = RecordingDelivery()
        // 引き当てを済ませた後にアカウントが消え、その後で投函された形
        repositories.enqueue(inboxes = listOf("https://a.example/inbox"), username = DELETED_USERNAME)

        runWorker(
            repositories.deliveryQueue,
            delivery,
            deletedActorDirectory = deletedActorDirectory(listOf(DELETED_USERNAME)),
        )

        // 送ると、消したことを伝えた後から投稿が届く
        assertEquals(emptyList(), delivery.delivered)
        assertEquals(FakeDeliveryQueueRepository.State.FAILED, repositories.deliveryQueue.rows().single().state)
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
            deletedActorDirectory = deletedActorDirectory(),
            idleInterval = IDLE,
            clock = { current },
            retryPolicy = TEST_RETRY_POLICY,
            backfill = backfillPublisher(delivery),
            circuitBreaker = DeliveryCircuitBreaker(failureThreshold = 10, coolOff = 60.seconds),
            domainBlocks = DomainBlockService(domainBlocks = FakeDomainBlockRepository(), clock = { now }),
            claimLimit = 8,
            sendConcurrency = 8,
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

        // 消えた inbox に 7 日送り続けない
        assertEquals(1, delivery.attempts)
        val row = repositories.deliveryQueue.rows().single()
        assertEquals(FakeDeliveryQueueRepository.State.FAILED, row.state)
        assertEquals(null, row.nextAttemptAt)
        assertEquals("届かない", row.lastError)
    }

    @Test
    fun `投函から 7 日を過ぎた行は送らずに諦める`() = runTest {
        val repositories = FakeRepositories()
        val delivery = RecordingDelivery()
        repositories.enqueue(inboxes = listOf("https://a.example/inbox"))

        runWorker(repositories.deliveryQueue, delivery, clock = { now.plusSeconds(8L * 24 * 60 * 60) })

        assertEquals(FakeDeliveryQueueRepository.State.FAILED, repositories.deliveryQueue.rows().single().state)
        // 止まっていた間に期限を過ぎた投稿を、再起動後に突然届けない
        assertEquals(emptyList(), delivery.delivered)
        assertEquals(0, delivery.attempts)
    }

    @Test
    fun `期限までに送れず諦めたらそのドメインへの配信を止める`() = runTest {
        val repositories = FakeRepositories()
        val delivery = RecordingDelivery(failing = setOf("https://a.example/inbox"))
        repositories.enqueue(inboxes = listOf("https://a.example/inbox"))

        runWorker(
            repositories.deliveryQueue,
            delivery,
            retryPolicy = DeliveryRetryPolicy(initialInterval = 2.hours, maxInterval = 24.hours, giveUpAfter = 1.hours),
            domainBlocks = repositories.domainBlocks,
        )

        val block = assertNotNull(repositories.domainBlocks.find("a.example"))
        assertEquals(DomainBlockReason.UNAVAILABLE, block.reason)
        assertTrue(block.blockDelivery)
        assertFalse(block.blockInbox)
    }

    @Test
    fun `投函から時間が経ちすぎて諦めたらそのドメインへの配信を止める`() = runTest {
        val repositories = FakeRepositories()
        repositories.enqueue(inboxes = listOf("https://a.example/inbox"))

        runWorker(
            repositories.deliveryQueue,
            RecordingDelivery(),
            clock = { now.plusSeconds(8L * 24 * 60 * 60) },
            domainBlocks = repositories.domainBlocks,
        )

        assertTrue(repositories.domainBlocks.blocksDelivery("a.example"))
    }

    @Test
    fun `相手が受け取らないと決めた失敗ではドメインを止めない`() = runTest {
        val repositories = FakeRepositories()
        val delivery = RecordingDelivery(failing = setOf("https://a.example/inbox"), retryable = false)
        repositories.enqueue(inboxes = listOf("https://a.example/inbox"))

        runWorker(repositories.deliveryQueue, delivery, domainBlocks = repositories.domainBlocks)

        assertFalse(repositories.domainBlocks.blocksDelivery("a.example"))
    }

    @Test
    fun `配信を止めたドメイン宛てに積まれていた行は送らずに諦める`() = runTest {
        val repositories = FakeRepositories()
        val delivery = RecordingDelivery()
        repositories.enqueue(inboxes = listOf("https://a.example/inbox", "https://b.example/inbox"))
        repositories.domainBlocks.saveManual(
            domain = "a.example",
            blockDelivery = true,
            blockInbox = true,
            description = null,
            at = now,
        )

        runWorker(repositories.deliveryQueue, delivery, domainBlocks = repositories.domainBlocks)

        assertEquals(listOf("https://b.example/inbox"), delivery.delivered)
        val row = repositories.deliveryQueue.rows().single()
        assertEquals("https://a.example/inbox", row.inbox)
        assertEquals(FakeDeliveryQueueRepository.State.FAILED, row.state)
    }

    @Test
    fun `送れたら自動で止めていたドメインは外し 手で止めたものは残す`() = runTest {
        val repositories = FakeRepositories()
        val delivery = RecordingDelivery()
        repositories.enqueue(inboxes = listOf("https://a.example/inbox", "https://b.example/inbox"))
        repositories.domainBlocks.markUnavailable(domain = "a.example", description = "諦めた", at = now)
        repositories.domainBlocks.saveManual(
            domain = "b.example",
            blockDelivery = true,
            blockInbox = true,
            description = null,
            at = now,
        )

        runWorker(
            repositories.deliveryQueue,
            delivery,
            domainBlocks = BlockedAfterSendCheck(repositories.domainBlocks),
        )

        assertEquals(2, delivery.delivered.size)
        assertEquals(null, repositories.domainBlocks.find("a.example"))
        assertEquals(DomainBlockReason.MANUAL, assertNotNull(repositories.domainBlocks.find("b.example")).reason)
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
            deletedActorDirectory = deletedActorDirectory(),
            idleInterval = IDLE,
            clock = { now },
            retryPolicy = TEST_RETRY_POLICY,
            backfill = backfillPublisher(delivery),
            circuitBreaker = DeliveryCircuitBreaker(failureThreshold = 10, coolOff = 60.seconds),
            domainBlocks = DomainBlockService(domainBlocks = FakeDomainBlockRepository(), clock = { now }),
            claimLimit = 8,
            sendConcurrency = 8,
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
            deletedActorDirectory = deletedActorDirectory(),
            idleInterval = IDLE,
            clock = { current },
            retryPolicy = TEST_RETRY_POLICY,
            backfill = backfillPublisher(delivery),
            circuitBreaker = DeliveryCircuitBreaker(failureThreshold = 10, coolOff = 60.seconds),
            domainBlocks = DomainBlockService(domainBlocks = FakeDomainBlockRepository(), clock = { now }),
            claimLimit = 8,
            sendConcurrency = 8,
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
            deletedActorDirectory = deletedActorDirectory(),
            idleInterval = IDLE,
            clock = { now },
            retryPolicy = TEST_RETRY_POLICY,
            backfill = backfillPublisher(delivery),
            circuitBreaker = DeliveryCircuitBreaker(failureThreshold = 10, coolOff = 60.seconds),
            domainBlocks = DomainBlockService(domainBlocks = FakeDomainBlockRepository(), clock = { now }),
            claimLimit = 8,
            sendConcurrency = 8,
        )

        val job = worker.start(this)
        advanceTimeBy(IDLE * 5)
        job.cancelAndJoin()

        assertEquals(emptyList(), delivery.delivered)
        assertEquals(emptyList(), repositories.deliveryQueue.rows())
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
    fun `同時に送るのは送り手の数まで`() = runTest {
        val repositories = FakeRepositories()
        val delivery = RecordingDelivery(latency = 100.milliseconds)
        repositories.enqueue(inboxes = (1..20).map { "https://host$it.example/inbox" })

        runWorker(repositories.deliveryQueue, delivery, claimLimit = 100, sendConcurrency = 8)

        assertEquals(8, delivery.maxConcurrent)
        assertEquals(20, delivery.delivered.size)
    }

    @Test
    fun `遅い宛先があっても 空いた送り手が次の行を送る`() = runTest {
        val repositories = FakeRepositories()
        val slowInbox = "https://slow.example/inbox"
        val delivery = RecordingDelivery(
            latency = 100.milliseconds,
            latencyByInbox = mapOf(slowInbox to 10.seconds),
        )
        repositories.enqueue(inboxes = listOf(slowInbox) + (1..3).map { "https://host$it.example/inbox" })
        val worker = DeliveryWorker(
            queue = repositories.deliveryQueue,
            delivery = delivery,
            directory = TestLocalActor.directory,
            deletedActorDirectory = deletedActorDirectory(),
            idleInterval = IDLE,
            clock = { now },
            retryPolicy = TEST_RETRY_POLICY,
            backfill = backfillPublisher(delivery),
            circuitBreaker = DeliveryCircuitBreaker(failureThreshold = 10, coolOff = 60.seconds),
            domainBlocks = DomainBlockService(domainBlocks = FakeDomainBlockRepository(), clock = { now }),
            claimLimit = 100,
            sendConcurrency = 2,
        )

        val job = worker.start(this)
        advanceTimeBy(1.seconds)
        job.cancelAndJoin()

        // 遅い 1 件が送り終わるのを待たずに、もう 1 本が残りを順に送る
        assertEquals((1..3).map { "https://host$it.example/inbox" }, delivery.delivered)
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
    fun `同じ inbox で失敗が続いたら 送らずに送り直し待ちへ回す`() = runTest {
        val repositories = FakeRepositories()
        val inbox = "https://a.example/inbox"
        val delivery = RecordingDelivery(failing = setOf(inbox))
        repositories.enqueue(inboxes = List(11) { inbox })

        runWorker(repositories.deliveryQueue, delivery, sendConcurrency = 1)

        // 10 回続けて失敗した後の 1 件は送りに行かない
        assertEquals(10, delivery.attempts)
        val rows = repositories.deliveryQueue.rows()
        assertTrue(rows.all { it.state == FakeDeliveryQueueRepository.State.PENDING })
        assertEquals("失敗が続いているので送らずに待つ", rows.last().lastError)
    }

    @Test
    fun `claim の中の span は出さず 1 行ごとの span を root で出す`() = runTest {
        val repositories = FakeRepositories()
        val delivery = RecordingDelivery()
        repositories.enqueue(inboxes = listOf("https://a.example/inbox"))
        val exporter = RecordingSpanExporter()
        val openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                    .build(),
            )
            .build()
        val worker = DeliveryWorker(
            queue = TracingClaim(repositories.deliveryQueue, openTelemetry),
            delivery = delivery,
            directory = TestLocalActor.directory,
            deletedActorDirectory = deletedActorDirectory(),
            idleInterval = IDLE,
            clock = { now },
            retryPolicy = TEST_RETRY_POLICY,
            backfill = backfillPublisher(delivery),
            circuitBreaker = DeliveryCircuitBreaker(failureThreshold = 10, coolOff = 60.seconds),
            domainBlocks = DomainBlockService(domainBlocks = FakeDomainBlockRepository(), clock = { now }),
            claimLimit = 8,
            sendConcurrency = 8,
            openTelemetry = openTelemetry,
        )

        val job = worker.start(this)
        advanceTimeBy(IDLE * 5)
        job.cancelAndJoin()

        // 空振りを含めて claim は何度も呼ばれるが、出るのは送った 1 行分だけ
        val span = exporter.spans.single()
        assertEquals("DeliveryWorker.deliver", span.name)
        assertEquals(false, span.parentSpanContext.isValid)
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
            deletedActorDirectory = deletedActorDirectory(),
            idleInterval = IDLE,
            clock = { now },
            retryPolicy = TEST_RETRY_POLICY,
            backfill = backfillPublisher(delivery),
            circuitBreaker = DeliveryCircuitBreaker(failureThreshold = 10, coolOff = 60.seconds),
            domainBlocks = DomainBlockService(domainBlocks = FakeDomainBlockRepository(), clock = { now }),
            claimLimit = 8,
            sendConcurrency = 8,
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
        sendConcurrency: Int = 8,
        clock: () -> Instant = { now },
        retryPolicy: DeliveryRetryPolicy = TEST_RETRY_POLICY,
        notes: FakeNoteStore = FakeNoteStore(),
        deletedActorDirectory: ActorDirectory = deletedActorDirectory(),
        domainBlocks: DomainBlockRepository = FakeDomainBlockRepository(),
    ) {
        val worker = DeliveryWorker(
            queue = queue,
            delivery = delivery,
            directory = TestLocalActor.directory,
            deletedActorDirectory = deletedActorDirectory,
            retryPolicy = retryPolicy,
            backfill = backfillPublisher(delivery = delivery, notes = notes),
            circuitBreaker = DeliveryCircuitBreaker(failureThreshold = 10, coolOff = 60.seconds),
            domainBlocks = DomainBlockService(domainBlocks = domainBlocks, clock = clock),
            idleInterval = IDLE,
            claimLimit = claimLimit,
            sendConcurrency = sendConcurrency,
            clock = clock,
        )
        val job: Job = worker.start(this)
        // 送るのに掛かる時間を進めて、空振りの待ちまで回してから止める
        advanceTimeBy(IDLE * 10 + 10.seconds)
        job.cancelAndJoin()
    }

    private fun incomingFollow(): IncomingFollow = IncomingFollow(
        username = TestLocalActor.USERNAME,
        follower = NewRemoteActor(
            actorUri = FOLLOWER_ACTOR_URI,
            inbox = FOLLOWER_INBOX,
            sharedInbox = null,
            publicKeyPem = "pem",
            profile = RemoteActorProfile(preferredUsername = null, displayName = null, profileUrl = null, iconUrl = null),
        ),
        followActivityUri = "$FOLLOWER_ACTOR_URI/follows/1",
        receivedAt = now,
        acceptBody = """{"type":"Accept"}""",
    )

    /**
     * 消したアカウントの引き先。既定では 1 つも消えていない
     */
    private fun deletedActorDirectory(deleted: List<String> = emptyList()): ActorDirectory = ActorDirectory(
        domain = TestLocalActor.DOMAIN,
        stored = FakeStoredActorNames(storedUserNames = deleted),
    )

    private fun backfillPublisher(
        delivery: RecordingDelivery,
        notes: FakeNoteStore = FakeNoteStore(),
    ): FollowBackfillPublisher = FollowBackfillPublisher(
        notes = notes,
        delivery = delivery,
        webPages = TestWebPageUrls,
    )

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
     * 送る直前に確かめた後で止めた状態を作る。送る直前の確認を通り抜けて送られる
     */
    private class BlockedAfterSendCheck(
        private val delegate: FakeDomainBlockRepository,
    ) : DomainBlockRepository by delegate {
        override fun blocksDelivery(domain: String): Boolean = false
    }

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
     * claim の中で SQL の span が作られる状態を作る
     */
    private class TracingClaim(
        private val delegate: FakeDeliveryQueueRepository,
        openTelemetry: OpenTelemetry,
    ) : DeliveryQueueRepository by delegate {
        private val tracer = openTelemetry.getTracer("jdbc")

        override fun claim(
            now: Instant,
            limit: Int,
        ): List<ClaimedDelivery> {
            val span = tracer.spanBuilder("SELECT delivery_queue").startSpan()
            try {
                return delegate.claim(now = now, limit = limit)
            } finally {
                span.end()
            }
        }
    }

    private class RecordingSpanExporter : SpanExporter {
        val spans = mutableListOf<SpanData>()

        override fun export(spans: Collection<SpanData>): CompletableResultCode {
            this.spans += spans
            return CompletableResultCode.ofSuccess()
        }

        override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()

        override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
    }

    /**
     * 送れた記録だけ 1 回失敗させる
     */
    private class FailingMarkDeliveredOnce(
        private val delegate: FakeDeliveryQueueRepository,
    ) : DeliveryQueueRepository by delegate {
        private var failed = false

        override fun markDelivered(
            id: DeliveryId,
            deliveredAt: Instant,
        ): DeliveredOutcome {
            if (!failed) {
                failed = true
                throw IllegalStateException("DB がロックされている")
            }
            return delegate.markDelivered(id = id, deliveredAt = deliveredAt)
        }
    }

    /**
     * 送信の差し替え。同時に何件送っているかを数える
     *
     * @param failing 失敗を返す宛先
     * @param failTimes 失敗を返す回数。0 なら毎回
     * @param throwing 例外を投げる宛先
     * @param latency 1 件に掛かる時間。並列の確認に使う
     * @param latencyByInbox 宛先ごとに [latency] の代わりに掛ける時間
     * @param retryable 失敗を送り直せるものとして返すか
     */
    private class RecordingDelivery(
        private val failing: Set<String> = emptySet(),
        private val failTimes: Int = 0,
        private val throwing: Set<String> = emptySet(),
        private val latency: kotlin.time.Duration = kotlin.time.Duration.ZERO,
        private val latencyByInbox: Map<String, kotlin.time.Duration> = mapOf(),
        private val retryable: Boolean = true,
    ) : ActivityDelivery {
        val delivered = mutableListOf<String>()
        val senders = mutableListOf<ActorUrls>()
        var attempts = 0
            private set
        var maxConcurrent = 0
            private set

        private var concurrent = 0
        private var failed = 0

        override suspend fun deliver(
            inbox: String,
            sender: ActorUrls,
            body: ByteArray,
        ): DeliveryResult {
            attempts++
            concurrent++
            maxConcurrent = maxOf(maxConcurrent, concurrent)
            try {
                delay(latencyByInbox[inbox] ?: latency)
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
            }
        }

        override fun close() {
        }
    }

    private companion object {
        const val DELETED_USERNAME = "gone"
        const val FOLLOWER_ACTOR_URI = "https://a.example/users/alice"
        const val FOLLOWER_INBOX = "https://a.example/users/alice/inbox"

        val IDLE = 100.milliseconds

        // 本番と同じ間隔。テストは時刻を自分で進めるので、実際に待つことはない
        val TEST_RETRY_POLICY = DeliveryRetryPolicy(
            initialInterval = 30.seconds,
            maxInterval = 24.hours,
            giveUpAfter = 7.days,
        )
    }
}
