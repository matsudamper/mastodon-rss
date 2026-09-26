package net.matsudamper.mastodon.rss.delivery

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceId
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import net.matsudamper.mastodon.rss.actor.ActorDirectory
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.logic.DomainBlockService
import net.matsudamper.mastodon.rss.note.FollowBackfillPublisher
import net.matsudamper.mastodon.rss.repository.ClaimedDelivery
import net.matsudamper.mastodon.rss.repository.DeliveredOutcome
import net.matsudamper.mastodon.rss.repository.DeliveryKind
import net.matsudamper.mastodon.rss.repository.DeliveryQueueRepository
import org.slf4j.LoggerFactory

/**
 * 配信キューの行を拾って相手の inbox に送る。
 *
 * リクエストとは無関係に動くので Ktor の routing には乗せず、起動時に 1 つだけ回す。
 * 同じ DB に対してプロセスを 2 つ動かす構成は取らない。起動時の復旧が、
 * 動いている他のプロセスの送信中の行まで巻き戻して二重に送る。
 *
 * claim した行は [sendConcurrency] 本のコルーチンで分け合って送る。1 本が空くたびに次の行を渡すので、
 * 遅い宛先が 1 件あっても他の行は止まらない。同時に送る数はこの本数で頭打ちになり、
 * フォロワーが増えても接続は青天井にならない
 *
 * キャンセルされたら送信中の行は `delivering` のまま残し、次の起動の復旧に任せる。
 * HTTP のタイムアウトは停止に使える時間より長いので、送り終わるのを待たない。
 *
 * @param directory 署名するこちらのアカウントの引き先
 * @param deletedActorDirectory 消したアカウントの引き先。消したことを伝える `Delete{Actor}` は
 *   これで引いて署名する
 * @param backfill フォローが成立した相手に過去の投稿を配る。成立するのは `Accept` が
 *   届いたときなので、始められるのはここになる
 * @param circuitBreaker 失敗が続いている inbox に送らずに済ませる
 * @param domainBlocks 配信を止めているドメイン。諦めた宛先のドメインをここで止め、
 *   送れたら再開する
 * @param claimLimit 1 回の claim で取り出す数。取った行を渡し終えるまで次の claim はしない
 * @param sendConcurrency 同時に送る数の上限
 * @param idleInterval claim が 0 件だったときに次を見に行くまでの待ち。
 *   新しい投稿が入ってから送り始めるまでの遅れの上限になる
 * @param openTelemetry 1 行ごとの span を出す先。1 行ごとに root を作る
 */
class DeliveryWorker(
    private val queue: DeliveryQueueRepository,
    private val delivery: ActivityDelivery,
    private val directory: ActorDirectory,
    private val deletedActorDirectory: ActorDirectory,
    private val retryPolicy: DeliveryRetryPolicy,
    private val backfill: FollowBackfillPublisher,
    private val circuitBreaker: DeliveryCircuitBreaker,
    private val domainBlocks: DomainBlockService,
    private val claimLimit: Int,
    private val sendConcurrency: Int,
    private val idleInterval: Duration,
    private val clock: () -> Instant,
    openTelemetry: OpenTelemetry = OpenTelemetry.noop(),
) {
    private val tracer = openTelemetry.getTracer("activitypub-delivery")

    /**
     * inbox ごとの最後に送れた時刻。
     *
     * 同じ inbox の行も並べて送るので、新しい行が送れた後で、期限間近の古い行の失敗が
     * 返ってくることがある。その失敗でドメインを止めると、動いている相手への配信が止まる。
     * 状態はメモリに持つ。再起動で消えても、次に送れたときにまた記録される
     */
    private val lastDeliveredAt = ConcurrentHashMap<String, Instant>()

    /**
     * 送れたことの記録と止めるかの判断を、ドメインの記録と合わせて 1 つずつ行う。
     * 分けると、判断した後に送れた記録が割り込み、送れた後から止まる
     */
    private val domainAvailabilityLock = Any()

    // 既定のサンプラー（parentbased）は親の判定を引き継ぐので、この下で作られた span は送られない
    private val unsampledContext: Context = Context.root().with(
        Span.wrap(
            SpanContext.create(
                TraceId.fromLongs(Random.nextLong(), Random.nextLong()),
                SpanId.fromLong(Random.nextLong()),
                TraceFlags.getDefault(),
                TraceState.getDefault(),
            ),
        ),
    )

    /**
     * 送信中のまま残っている行を戻してから、繰り返しを始める
     */
    fun start(scope: CoroutineScope): Job =
        scope.launch {
            // 成立したフォローへの配り直しをここに乗せる。送り手のコルーチンの中で待つと、
            // 過去の投稿を配り終えるまでその 1 本が次の行を受け取れない
            val backfillScope = this
            recoverDelivering()

            // バッファを持たせず、送り手が空いたときにだけ 1 行渡す
            val claimedRows = Channel<ClaimedDelivery>()
            repeat(sendConcurrency) {
                launch {
                    for (row in claimedRows) {
                        deliverOneInSpan(row = row, backfillScope = backfillScope)
                    }
                }
            }

            while (true) {
                val claimed = claimOrEmpty()
                if (claimed.isEmpty()) {
                    delay(idleInterval)
                    continue
                }

                claimed.forEach { claimedRows.send(it) }
            }
        }

    /**
     * 送信中のまま残っている行を戻す。戻せるまで繰り返す。
     *
     * 投げると繰り返しが始まらず、次に再起動するまで配信が止まる
     */
    private suspend fun recoverDelivering() {
        while (true) {
            try {
                val count = queue.recoverDelivering()
                if (count > 0) {
                    logger.info("送信中のまま残っていた配信 $count 件を送り直す")
                }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn("送信中のまま残っていた配信を戻せなかった", e)
                delay(idleInterval)
            }
        }
    }

    /**
     * 引けなかったときは空として扱う。投げると繰り返しが終わり、次に再起動するまで配信が止まる。
     *
     * 何も無くても [idleInterval] ごとに呼ぶので、中の SQL の span は出さない。
     * 出すと、空振りの SQL が親の無いトレースとして並ぶ。取れた行は 1 行ごとの span で追える
     */
    private fun claimOrEmpty(): List<ClaimedDelivery> =
        try {
            unsampledContext.makeCurrent().use {
                queue.claim(now = clock(), limit = claimLimit)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("配信キューを引けなかった", e)
            listOf()
        }

    /**
     * 送信の HTTP の span をこの行の span の下にまとめる。無いと POST だけがばらばらに出て、
     * どの行の何回目の送信かが分からない
     */
    private suspend fun deliverOneInSpan(
        row: ClaimedDelivery,
        backfillScope: CoroutineScope,
    ) {
        val span =
            tracer.spanBuilder("DeliveryWorker.deliver")
                // claim を包んだ送らない親を拾うと、この行の span まで送られなくなる
                .setNoParent()
                .setAttribute(DeliverySpan.ID, row.id.value)
                .setAttribute(DeliverySpan.KIND, row.kind.name)
                .setAttribute(DeliverySpan.SENDER, row.username)
                .setAttribute(DeliverySpan.INBOX, row.inbox)
                .setAttribute(DeliverySpan.ATTEMPTS, row.attempts.toLong())
                .startSpan()
        try {
            withContext(Context.current().with(span).asContextElement()) {
                deliverOne(row = row, backfillScope = backfillScope)
            }
        } finally {
            span.end()
        }
    }

    /**
     * 1 件送って結果を記録する。
     *
     * 例外はこの行の失敗として扱う。キャンセルだけは投げ直して、失敗として記録しない。
     * 記録すると、送れていない行が `pending` に戻って停止中に送り直される
     */
    private suspend fun deliverOne(
        row: ClaimedDelivery,
        backfillScope: CoroutineScope,
    ) {
        try {
            val sender = resolveSender(row)
            if (sender == null) {
                DeliverySpan.outcome("gave_up.no_account")
                queue.giveUp(row.id, "アカウントが無い: ${row.username}")
                logger.warn("配信を諦めた: アカウントが無い ${row.username} → ${row.inbox}")
                return
            }

            // claim した後に投稿が消されると、行ごと消える。claim から送り始めるまでは開くので、
            // 送る直前に確かめる。残るのは送っている最中に消された場合だけになる
            if (!queue.exists(row.id)) {
                DeliverySpan.outcome("skipped.deleted")
                logger.info("配信を取りやめた: 投稿が消えている ${row.username} → ${row.inbox}")
                return
            }

            // 投函より後に止めたドメイン宛ての行が残っている
            if (domainBlocks.blocksDeliveryTo(row.inbox)) {
                DeliverySpan.outcome("gave_up.domain_blocked")
                queue.giveUp(row.id, "配信を止めているドメイン")
                logger.info("配信を諦めた: 配信を止めているドメイン ${row.username} → ${row.inbox}")
                return
            }

            // 止まっていた間に期限を過ぎた行を送らない。送ると 1 週間以上前の投稿が突然届く。
            // こちらが止まっていただけで相手に送れなかったとは限らないので、ドメインは止めない
            if (retryPolicy.isExpired(enqueuedAt = row.enqueuedAt, now = clock())) {
                DeliverySpan.outcome("gave_up.expired")
                queue.giveUp(row.id, "投函から時間が経ちすぎた")
                logger.warn("配信を諦めた: 投函から時間が経ちすぎた ${row.username} → ${row.inbox}")
                return
            }

            if (circuitBreaker.isOpen(inbox = row.inbox, now = clock())) {
                val reason = "失敗が続いているので送らずに待つ"
                DeliverySpan.failed(reason)
                recordFailure(row, reason)
                return
            }

            val result = try {
                delivery.deliver(inbox = row.inbox, sender = sender, body = row.body.toByteArray())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DeliveryResult.Failed(reason = "送信で例外が出た: ${e.message}", retryable = true)
            }

            when (result) {
                is DeliveryResult.Delivered -> {
                    DeliverySpan.outcome("delivered")
                    circuitBreaker.recordSuccess(row.inbox)
                    val outcome = queue.markDelivered(id = row.id, deliveredAt = clock())
                    markAvailable(inbox = row.inbox, deliveredAt = clock())
                    when (outcome) {
                        DeliveredOutcome.None -> Unit

                        is DeliveredOutcome.FollowAccepted -> {
                            logger.info("Accept が届いてフォローが成立した: ${sender.acct} ← ${outcome.followerActorUri}")
                            backfillRecentNotes(sender = sender, accepted = outcome, scope = backfillScope)
                        }
                    }
                }

                is DeliveryResult.Failed -> {
                    DeliverySpan.failed(result.reason)
                    // 相手が受け取らないと決めた応答は、間を空けても同じ答えが返る。
                    // 消えた inbox に 7 日送り続けても届かない
                    if (result.retryable) {
                        circuitBreaker.recordFailure(inbox = row.inbox, now = clock())
                        recordFailure(row, result.reason)
                    } else {
                        DeliverySpan.outcome("gave_up.rejected")
                        queue.giveUp(row.id, result.reason)
                        logger.warn("配信を諦めた: 相手が受け取らない ${row.username} → ${row.inbox} ${result.reason}")
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // DB への記録で落ちた分。delivering のまま残すと、起動時の復旧までこの行は二度と
            // claim されない。送り直しの時刻を付けて pending に戻すのをもう一度だけ試す。
            // 送れていた行を戻すと二重に届くことがあるが、受信側は id で冪等に扱う
            DeliverySpan.outcome("record_failed")
            DeliverySpan.failed(e)
            logger.error("配信の結果を記録できなかった: ${row.id.value} → ${row.inbox}", e)
            releaseToRetry(row, "結果を記録できなかった: ${e.message}")
        }
    }

    /**
     * 署名するこちらのアカウントを引く。
     *
     * 消したアカウントとして署名してよいのは、消したことを伝える `Delete{Actor}` だけ。
     * 他の種別まで引けると、消した後に投函された行が旧アクターとして送られて、
     * `Delete` の後から投稿が届く
     */
    private fun resolveSender(row: ClaimedDelivery): ActorUrls? {
        directory.resolve(row.username)?.let { return it }
        if (row.kind != DeliveryKind.DELETE_ACTOR) return null

        return deletedActorDirectory.resolve(row.username)
    }

    /**
     * フォローが成立した相手に、フォローより前の投稿を配る。
     *
     * 配信の結果は記録済みなので、ここで落ちてもキューには残らない。配れなくても
     * フォローは成立していて、次の新着からは普通に届く
     */
    private fun backfillRecentNotes(
        sender: ActorUrls,
        accepted: DeliveredOutcome.FollowAccepted,
        scope: CoroutineScope,
    ) {
        // 通常の配信はフォロワーとして数えられてから始まるので、境目は成立した後に取る。
        // 先に取ると、成立を待っている間の投稿がどちらからも漏れる。境目が重なって
        // 二重に送っても、同じ id なので相手側で落ちる
        val backfillUntil = clock()

        scope.launch {
            runCatching {
                backfill.deliverRecentNotes(
                    sender = sender,
                    inbox = accepted.inbox,
                    publishedBefore = backfillUntil,
                )
            }.onFailure { failure ->
                logger.warn("過去の投稿を配れなかった: ${sender.acct} → ${accepted.followerActorUri}", failure)
            }
        }
    }

    /**
     * 記録に失敗した行を送り直し待ちに戻す。ここでも落ちたら諦めて delivering のまま残す
     */
    private fun releaseToRetry(
        row: ClaimedDelivery,
        reason: String,
    ) {
        try {
            val now = clock()
            val nextAttemptAt = retryPolicy.nextAttemptAt(attempts = row.attempts, enqueuedAt = row.enqueuedAt, now = now) ?: now
            queue.scheduleRetry(row.id, nextAttemptAt = nextAttemptAt, error = reason)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("配信を送り直し待ちに戻せなかった。次の起動まで送られない: ${row.id.value} → ${row.inbox}", e)
        }
    }

    private fun recordFailure(
        row: ClaimedDelivery,
        reason: String,
    ) {
        val nextAttemptAt = retryPolicy.nextAttemptAt(attempts = row.attempts, enqueuedAt = row.enqueuedAt, now = clock())
        if (nextAttemptAt == null) {
            DeliverySpan.outcome("gave_up.retry_exhausted")
            queue.giveUp(row.id, reason)
            logger.warn("配信を諦めた: ${row.username} → ${row.inbox} ${row.attempts} 回目 $reason")
            markUnavailable(row = row, reason = reason)
            return
        }

        DeliverySpan.outcome("retry_scheduled")
        queue.scheduleRetry(row.id, nextAttemptAt = nextAttemptAt, error = reason)
        logger.warn("配れなかったので $nextAttemptAt に送り直す: ${row.username} → ${row.inbox} ${row.attempts} 回目 $reason")
    }

    /**
     * 期限まで送れなかったので、そのドメインへの配信を止める。
     *
     * この行を投函した後に同じ inbox へ送れていたら止めない。相手は動いている。
     *
     * 行の結果は記録済みなので、ここで落ちても行を送り直し待ちには戻さない。
     * 止められなくても、次に諦めたときにまた試す
     */
    private fun markUnavailable(
        row: ClaimedDelivery,
        reason: String,
    ) {
        try {
            synchronized(domainAvailabilityLock) {
                val deliveredAt = lastDeliveredAt[row.inbox]
                if (deliveredAt != null && !deliveredAt.isBefore(row.enqueuedAt)) {
                    logger.info("諦めた後も送れているのでドメインは止めない: ${row.inbox}")
                    return
                }
                domainBlocks.markUnavailable(inbox = row.inbox, reason = reason)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("配信を止めるドメインを記録できなかった: ${row.inbox}", e)
        }
    }

    /**
     * 送れたので、自動で止めていたドメインへの配信を再開する。
     *
     * 行の結果は記録済みなので、ここで落ちても行を送り直し待ちには戻さない
     */
    private fun markAvailable(
        inbox: String,
        deliveredAt: Instant,
    ) {
        try {
            synchronized(domainAvailabilityLock) {
                lastDeliveredAt[inbox] = deliveredAt
                domainBlocks.markAvailable(inbox)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("配信を再開するドメインを記録できなかった: $inbox", e)
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(DeliveryWorker::class.java)
    }
}
