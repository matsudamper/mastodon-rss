package net.matsudamper.mastodon.rss.delivery

import java.time.Instant
import kotlin.math.min
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * 送れなかった配信を次にいつ送るか。
 *
 * 間隔は 30 秒から 2 倍ずつ伸ばして 24 時間で頭打ちにする。1 日半ほどで 24 時間間隔に届き、
 * その後は 1 日 1 回で投函から 30 日まで続く。相手が数日止まっていても復帰後に届き、
 * 恒久的に消えたインスタンスは 1 か月で切れる。
 *
 * @param initialInterval 最初の失敗の後に待つ時間
 * @param maxInterval 間隔の上限
 * @param giveUpAfter 投函からこれを過ぎる時刻に送ることになるなら諦める
 */
class DeliveryRetryPolicy(
    private val initialInterval: Duration,
    private val maxInterval: Duration,
    private val giveUpAfter: Duration,
) {
    /**
     * 投函から時間が経ちすぎていて、もう送るべきでないか。
     *
     * 止まっていた間に期限を過ぎた行を、再起動後に 1 回だけ送ってしまわないために送る前に見る
     */
    fun isExpired(
        enqueuedAt: Instant,
        now: Instant,
    ): Boolean = now.isAfter(enqueuedAt.plus(giveUpAfter.toJavaDuration()))

    /**
     * @param attempts claim された回数。1 回目の失敗なら 1
     * @return 次に送る時刻。諦めるなら null
     */
    fun nextAttemptAt(
        attempts: Int,
        enqueuedAt: Instant,
        now: Instant,
    ): Instant? {
        val exponent = (attempts - 1).coerceAtLeast(0)
        // 2 の べき乗で Long が溢れないよう、上限で切ってから整数に戻す
        val intervalMillis = min(
            initialInterval.inWholeMilliseconds.toDouble() * 2.0.pow(exponent),
            maxInterval.inWholeMilliseconds.toDouble(),
        ).toLong()

        val next = now.plusMillis(intervalMillis)
        if (next.isAfter(enqueuedAt.plus(giveUpAfter.toJavaDuration()))) return null
        return next
    }
}
