package net.matsudamper.mastodon.rss.delivery

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// 送れなかった配信を次にいつ送るか。
// 間隔が 2 倍ずつ伸びて 24 時間で頭打ちになること、30 日で諦めることが要件。
class DeliveryRetryPolicyTest {
    private val policy = DeliveryRetryPolicy()

    private val enqueuedAt: Instant = Instant.parse("2026-08-10T00:00:00Z")

    @Test
    fun `失敗するたびに間隔が 2 倍になる`() {
        val first = assertNotNull(policy.nextAttemptAt(attempts = 1, enqueuedAt = enqueuedAt, now = enqueuedAt))
        val second = assertNotNull(policy.nextAttemptAt(attempts = 2, enqueuedAt = enqueuedAt, now = enqueuedAt))
        val third = assertNotNull(policy.nextAttemptAt(attempts = 3, enqueuedAt = enqueuedAt, now = enqueuedAt))

        assertEquals(Duration.ofSeconds(30), Duration.between(enqueuedAt, first))
        assertEquals(Duration.ofSeconds(60), Duration.between(enqueuedAt, second))
        assertEquals(Duration.ofSeconds(120), Duration.between(enqueuedAt, third))
    }

    @Test
    fun `間隔は 24 時間で頭打ちになる`() {
        // 30 秒 × 2^12 = 34 時間強なので、13 回目から上限に当たる
        val capped = assertNotNull(policy.nextAttemptAt(attempts = 13, enqueuedAt = enqueuedAt, now = enqueuedAt))
        val farLater = assertNotNull(policy.nextAttemptAt(attempts = 100, enqueuedAt = enqueuedAt, now = enqueuedAt))

        assertEquals(Duration.ofHours(24), Duration.between(enqueuedAt, capped))
        assertEquals(Duration.ofHours(24), Duration.between(enqueuedAt, farLater))
    }

    @Test
    fun `次に送る時刻が投函から 30 日を過ぎるなら諦める`() {
        val lastChance = enqueuedAt.plus(Duration.ofDays(29))
        assertNotNull(policy.nextAttemptAt(attempts = 20, enqueuedAt = enqueuedAt, now = lastChance))

        val tooLate = enqueuedAt.plus(Duration.ofDays(29)).plus(Duration.ofHours(1))
        assertNull(policy.nextAttemptAt(attempts = 20, enqueuedAt = enqueuedAt, now = tooLate))
    }

    @Test
    fun `claim される前の回数でも最初の間隔になる`() {
        val next = assertNotNull(policy.nextAttemptAt(attempts = 0, enqueuedAt = enqueuedAt, now = enqueuedAt))

        assertEquals(Duration.ofSeconds(30), Duration.between(enqueuedAt, next))
    }

    @Test
    fun `投函から 30 日を過ぎていれば期限切れ`() {
        assertEquals(false, policy.isExpired(enqueuedAt = enqueuedAt, now = enqueuedAt.plus(Duration.ofDays(30))))
        assertEquals(true, policy.isExpired(enqueuedAt = enqueuedAt, now = enqueuedAt.plus(Duration.ofDays(30)).plusSeconds(1)))
    }
}
