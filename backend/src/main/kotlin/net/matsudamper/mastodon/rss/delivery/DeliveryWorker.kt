package net.matsudamper.mastodon.rss.delivery

import java.time.Instant
import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.matsudamper.mastodon.rss.actor.ActorDirectory
import net.matsudamper.mastodon.rss.actor.ActorUrls
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
 * 1 回の claim を 1 まとまりとして並列に送る。claim はホストごとに 1 件しか返さないので、
 * 同じインスタンスに同時に投げることはない。まとまりの大きさが同時実行数の上限になり、
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
 * @param claimLimit 1 回の claim で取り出す数。同時に相手にするホストの数であり、同時実行数の上限でもある
 * @param idleInterval claim が 0 件だったときに次を見に行くまでの待ち。
 *   新しい投稿が入ってから送り始めるまでの遅れの上限になる
 */
class DeliveryWorker(
    private val queue: DeliveryQueueRepository,
    private val delivery: ActivityDelivery,
    private val directory: ActorDirectory,
    private val deletedActorDirectory: ActorDirectory,
    private val retryPolicy: DeliveryRetryPolicy,
    private val backfill: FollowBackfillPublisher,
    private val claimLimit: Int,
    private val idleInterval: Duration,
    private val clock: () -> Instant,
) {
    /**
     * 送信中のまま残っている行を戻してから、繰り返しを始める
     */
    fun start(scope: CoroutineScope): Job =
        scope.launch {
            // 成立したフォローへの配り直しをここに乗せる。1 回の claim の中で待つと、
            // 過去の投稿を配り終えるまで次の claim が始まらない
            val backfillScope = this
            // 復旧も繰り返しの中で試す。外で投げると繰り返しが始まらず、次に再起動するまで配信が止まる
            var recovered = false
            while (true) {
                if (!recovered) {
                    recovered = try {
                        val count = queue.recoverDelivering()
                        if (count > 0) {
                            logger.info("送信中のまま残っていた配信 $count 件を送り直す")
                        }
                        true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.warn("送信中のまま残っていた配信を戻せなかった", e)
                        delay(idleInterval)
                        continue
                    }
                }

                val claimed = try {
                    queue.claim(now = clock(), limit = claimLimit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // ここで投げると繰り返しが終わり、次に再起動するまで配信が止まる
                    logger.warn("配信キューを引けなかった", e)
                    delay(idleInterval)
                    continue
                }

                if (claimed.isEmpty()) {
                    delay(idleInterval)
                    continue
                }

                deliverAll(claimed = claimed, backfillScope = backfillScope)
            }
        }

    /**
     * claim した行を全部送り終わるまで返らない。
     *
     * 1 行に 1 本のコルーチンを当てる。宛先のホストは行ごとに違うので、
     * 同じインスタンスに 2 本同時に向かうことはない
     */
    private suspend fun deliverAll(
        claimed: List<ClaimedDelivery>,
        backfillScope: CoroutineScope,
    ) {
        coroutineScope {
            claimed.forEach { row ->
                launch { deliverOne(row = row, backfillScope = backfillScope) }
            }
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
                queue.giveUp(row.id, "アカウントが無い: ${row.username}")
                logger.warn("配信を諦めた: アカウントが無い ${row.username} → ${row.inbox}")
                return
            }

            // claim した後に投稿が消されると、行ごと消える。claim から送り始めるまでは開くので、
            // 送る直前に確かめる。残るのは送っている最中に消された場合だけになる
            if (!queue.exists(row.id)) {
                logger.info("配信を取りやめた: 投稿が消えている ${row.username} → ${row.inbox}")
                return
            }

            // 止まっていた間に期限を過ぎた行を送らない。送ると 1 か月以上前の投稿が突然届く
            if (retryPolicy.isExpired(enqueuedAt = row.enqueuedAt, now = clock())) {
                queue.giveUp(row.id, "投函から時間が経ちすぎた")
                logger.warn("配信を諦めた: 投函から時間が経ちすぎた ${row.username} → ${row.inbox}")
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
                    when (val outcome = queue.markDelivered(id = row.id, deliveredAt = clock())) {
                        DeliveredOutcome.None -> Unit

                        is DeliveredOutcome.FollowAccepted -> {
                            logger.info("Accept が届いてフォローが成立した: ${sender.acct} ← ${outcome.followerActorUri}")
                            backfillRecentNotes(sender = sender, accepted = outcome, scope = backfillScope)
                        }
                    }
                }

                is DeliveryResult.Failed -> {
                    // 相手が受け取らないと決めた応答は、間を空けても同じ答えが返る。
                    // 消えた inbox に 30 日送り続けても届かない
                    if (result.retryable) {
                        recordFailure(row, result.reason)
                    } else {
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
            queue.giveUp(row.id, reason)
            logger.warn("配信を諦めた: ${row.username} → ${row.inbox} ${row.attempts} 回目 $reason")
            return
        }

        queue.scheduleRetry(row.id, nextAttemptAt = nextAttemptAt, error = reason)
        logger.warn("配れなかったので $nextAttemptAt に送り直す: ${row.username} → ${row.inbox} ${row.attempts} 回目 $reason")
    }

    private companion object {
        val logger = LoggerFactory.getLogger(DeliveryWorker::class.java)
    }
}
