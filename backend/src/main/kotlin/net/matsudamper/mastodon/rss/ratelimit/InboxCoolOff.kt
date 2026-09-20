package net.matsudamper.mastodon.rss.ratelimit

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 署名を拒否した送信元を、しばらく inbox に通さない。
 *
 * inbox は無認証で誰でも POST できる。署名を検証するには `keyId` が指す URL を
 * 相手のサーバーへ取りに行くことになるので、通らない署名を送り続けるだけで、
 * こちらに GET を出させたり、取りに行った記録でメモリを使わせたりできる。
 * 検証より手前で送信元ごとに止めれば、どの経路もまとめて塞げる。
 *
 * 正当な配信は署名が通るので、ここには入らない。入るのは署名を拒否された
 * 送信元だけで、1 回拒否したらその時点で [coolOff] のあいだ通さない。
 * Mastodon も同じことを送信元 IP ごとに行っている（`STOPLIGHT_COOL_OFF_TIME`）。
 *
 * 記録はメモリにしか持たない。再起動で消えるが、止めたいのは続けて送ってくる
 * 相手なので、消えても次の 1 通でまた入る。
 *
 * @param coolOff 1 回拒否してから通さないでおく時間
 * @param maxClients 覚えておく送信元の数。送信元はいくらでも増やせるので上限で縛る
 * @param maxHistory 残しておく履歴の件数
 */
class InboxCoolOff(
    private val coolOff: Duration,
    private val maxClients: Int,
    private val maxHistory: Int,
    private val clock: Clock,
) {
    private val clients = ConcurrentHashMap<String, Client>()

    /**
     * 新しい順。件数が上限を超えたら古いものから捨てる
     */
    private val history = ArrayDeque<InboxCoolOffRecord>()

    /**
     * 通してよければ null。クールオフ中なら明ける時刻。
     *
     * 通さなかったことも数える。管理画面で「効いている」ことが分かるようにする
     */
    fun blockedUntil(clientIp: String): Instant? {
        val client = clients[clientIp] ?: return null

        val until = client.until
        if (!until.isAfter(clock.instant())) {
            // 明けた。次に拒否されるまでは通す
            clients.remove(clientIp, client)
            return null
        }

        client.blockedRequests.incrementAndGet()
        return until
    }

    /**
     * 署名を拒否したことを記録して、クールオフに入れる
     */
    fun rejected(clientIp: String) {
        val now = clock.instant()
        val until = now.plus(coolOff)

        clients.compute(clientIp) { _, existing ->
            existing?.renewed(until) ?: Client(until = until)
        }

        addHistory(
            InboxCoolOffRecord(
                clientIp = clientIp,
                blockedAt = now,
                until = until,
                rejectedCount = clients[clientIp]?.rejections?.get() ?: 1,
            ),
        )

        pruneClients()
    }

    /**
     * いまクールオフ中の送信元。明ける時刻が早い順
     */
    fun active(): List<InboxCoolOffState> {
        val now = clock.instant()

        return clients.entries
            .filter { it.value.until.isAfter(now) }
            .map { (clientIp, client) ->
                InboxCoolOffState(
                    clientIp = clientIp,
                    until = client.until,
                    rejectedCount = client.rejections.get(),
                    blockedRequestCount = client.blockedRequests.get(),
                )
            }.sortedBy { it.until }
    }

    /**
     * クールオフに入れた履歴。新しい順
     */
    fun history(limit: Int): List<InboxCoolOffRecord> =
        synchronized(history) {
            history.take(limit)
        }

    private fun addHistory(record: InboxCoolOffRecord) {
        synchronized(history) {
            history.addFirst(record)
            while (history.size > maxHistory) {
                history.removeLast()
            }
        }
    }

    /**
     * 覚えている送信元が多すぎたら、明けたものから捨てる。
     *
     * 明けたものだけでは足りなければ、クールオフ中のものも捨てる。捨てられた
     * 相手は次の 1 通でまた入るだけで、素通しになるわけではない
     */
    private fun pruneClients() {
        if (clients.size <= maxClients) return

        val now = clock.instant()
        clients.entries.removeIf { !it.value.until.isAfter(now) }

        var excess = clients.size - maxClients
        if (excess <= 0) return

        val iterator = clients.entries.iterator()
        while (excess > 0 && iterator.hasNext()) {
            val entry = iterator.next()
            if (clients.remove(entry.key, entry.value)) excess--
        }
    }

    private class Client(
        @Volatile var until: Instant,
    ) {
        val rejections: AtomicInteger = AtomicInteger(1)

        val blockedRequests: AtomicInteger = AtomicInteger()

        fun renewed(until: Instant): Client {
            this.until = until
            rejections.incrementAndGet()
            return this
        }
    }
}

/**
 * いまクールオフ中の送信元 1 つ。
 *
 * @param rejectedCount 署名を拒否した回数
 * @param blockedRequestCount 検証まで進めずに返したリクエストの数
 */
data class InboxCoolOffState(
    val clientIp: String,
    val until: Instant,
    val rejectedCount: Int,
    val blockedRequestCount: Int,
)

/**
 * クールオフに入れた 1 回分。
 *
 * @param rejectedCount その時点での、この送信元を拒否した回数
 */
data class InboxCoolOffRecord(
    val clientIp: String,
    val blockedAt: Instant,
    val until: Instant,
    val rejectedCount: Int,
)
