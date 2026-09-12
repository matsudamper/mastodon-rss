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
import net.matsudamper.mastodon.rss.repository.ClaimedDelivery
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
 * @param claimLimit 1 回の claim で取り出す数。同時に相手にするホストの数であり、同時実行数の上限でもある
 * @param idleInterval claim が 0 件だったときに次を見に行くまでの待ち。
 *   新しい投稿が入ってから送り始めるまでの遅れの上限になる
 */
class DeliveryWorker(
    private val queue: DeliveryQueueRepository,
    private val delivery: ActivityDelivery,
    private val directory: ActorDirectory,
    private val retryPolicy: DeliveryRetryPolicy,
    private val claimLimit: Int,
    private val idleInterval: Duration,
    private val clock: () -> Instant,
) {
    /**
     * 送信中のまま残っている行を戻してから、繰り返しを始める
     */
    fun start(scope: CoroutineScope): Job =
        scope.launch {
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

                deliverAll(claimed)
            }
        }

    /**
     * claim した行を全部送り終わるまで返らない。
     *
     * 1 行に 1 本のコルーチンを当てる。宛先のホストは行ごとに違うので、
     * 同じインスタンスに 2 本同時に向かうことはない
     */
    private suspend fun deliverAll(claimed: List<ClaimedDelivery>) {
        coroutineScope {
            claimed.forEach { row ->
                launch { deliverOne(row) }
            }
        }
    }

    /**
     * 1 件送って結果を記録する。
     *
     * 例外はこの行の失敗として扱う。キャンセルだけは投げ直して、失敗として記録しない。
     * 記録すると、送れていない行が `pending` に戻って停止中に送り直される
     */
    private suspend fun deliverOne(row: ClaimedDelivery) {
        try {
            val sender = directory.resolve(row.username)
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
                is DeliveryResult.Delivered -> queue.markDelivered(row.id)

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
