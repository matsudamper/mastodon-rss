package net.matsudamper.mastodon.rss.delivery

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * 失敗が続いている inbox へ、しばらく送らずに済ませる。
 *
 * 取り出しは宛先を散らさないので、落ちている相手宛の行が同時にいくつも送られる。
 * 続けて [failureThreshold] 回失敗したら、[coolOff] の間は送らずに送り直し待ちへ回す。
 * 間が明けた後も失敗の数は残るので、次の 1 回が失敗すればすぐにまた止める。成功で数え直す。
 *
 * Mastodon の配信（`ActivityPub::DeliveryWorker`）も inbox の URL ごとに同じ形で止めている
 * （10 回で 60 秒）。
 *
 * 状態はメモリに持つ。再起動で消えても、次の失敗からまた数えればよい
 */
class DeliveryCircuitBreaker(
    private val failureThreshold: Int,
    private val coolOff: Duration,
) {
    private val inboxes = ConcurrentHashMap<String, InboxState>()

    /**
     * いまは送らずに待つべきか
     */
    fun isOpen(
        inbox: String,
        now: Instant,
    ): Boolean {
        val openUntil = inboxes[inbox]?.openUntil ?: return false
        return now.isBefore(openUntil)
    }

    fun recordSuccess(inbox: String) {
        inboxes.remove(inbox)
    }

    fun recordFailure(
        inbox: String,
        now: Instant,
    ) {
        inboxes.compute(inbox) { _, current ->
            val consecutiveFailures = (current?.consecutiveFailures ?: 0) + 1
            InboxState(
                consecutiveFailures = consecutiveFailures,
                openUntil = if (consecutiveFailures >= failureThreshold) {
                    now.plus(coolOff.toJavaDuration())
                } else {
                    null
                },
            )
        }
    }

    private data class InboxState(
        val consecutiveFailures: Int,
        val openUntil: Instant?,
    )
}
