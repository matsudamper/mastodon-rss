package net.matsudamper.mastodon.rss.graphql.data

import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.matsudamper.mastodon.rss.repository.DeliveryQueuePosition
import net.matsudamper.mastodon.rss.repository.entity.DeliveryId

// 送り直し待ちの一覧の続きを指す印。
// 外から来る値なので、壊れていても投げずに読めなかったことにするのが要件。
class DeliveryQueueCursorTest {
    @Test
    fun `位置を包んで元に戻せる`() {
        val position = DeliveryQueuePosition(
            nextAttemptAt = Instant.parse("2026-08-10T00:00:00.123456789Z"),
            id = DeliveryId(42),
        )

        val decoded = DeliveryQueueCursor.decode(DeliveryQueueCursor.of(position).encode())

        assertEquals(position, decoded?.toPosition())
    }

    @Test
    fun `壊れた値は読めなかったことにする`() {
        assertNull(DeliveryQueueCursor.decode("これは base64 ではない"))
        assertNull(DeliveryQueueCursor.decode(encode("""{"afterEpochSecond":1}""")))
    }

    @Test
    fun `時刻にできない数値も読めなかったことにする`() {
        val outOfRange = """{"afterEpochSecond":${Long.MAX_VALUE},"afterNano":0,"afterId":1}"""

        assertNull(DeliveryQueueCursor.decode(encode(outOfRange)))
    }

    private fun encode(json: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(json.encodeToByteArray())
}
