package net.matsudamper.mastodon.rss.feed

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// 日時の読み取りを確認する。
// 配信元の書き方は仕様どおりとは限らないので、崩れた形も読めるところまで固定する。
class FeedDatesTest {
    @Test
    fun `RFC 822 の pubDate を読む`() {
        assertEquals(
            Instant.parse("2024-10-01T23:00:00Z"),
            FeedDates.parse("Wed, 02 Oct 2024 08:00:00 +0900"),
        )
    }

    @Test
    fun `曜日が無くても読む`() {
        assertEquals(
            Instant.parse("2024-10-02T08:00:00Z"),
            FeedDates.parse("02 Oct 2024 08:00:00 GMT"),
        )
    }

    @Test
    fun `日が 1 桁でも読む`() {
        assertEquals(
            Instant.parse("2024-10-02T08:00:00Z"),
            FeedDates.parse("Wed, 2 Oct 2024 08:00:00 GMT"),
        )
    }

    @Test
    fun `略称のタイムゾーンを読む`() {
        assertEquals(
            Instant.parse("2024-10-02T13:00:00Z"),
            FeedDates.parse("Wed, 02 Oct 2024 08:00:00 EST"),
        )
        assertEquals(
            Instant.parse("2024-10-01T23:00:00Z"),
            FeedDates.parse("Wed, 02 Oct 2024 08:00:00 JST"),
        )
    }

    @Test
    fun `知らない略称のタイムゾーンは UTC として読む`() {
        // 数時間ずれるが、日時ごと落とすよりは良い
        assertEquals(
            Instant.parse("2024-10-02T08:00:00Z"),
            FeedDates.parse("Wed, 02 Oct 2024 08:00:00 CET"),
        )
    }

    @Test
    fun `タイムゾーンが無ければ UTC として読む`() {
        assertEquals(
            Instant.parse("2024-10-02T08:00:00Z"),
            FeedDates.parse("Wed, 02 Oct 2024 08:00:00"),
        )
    }

    @Test
    fun `RFC 3339 の日時を読む`() {
        assertEquals(Instant.parse("2024-10-01T23:00:00Z"), FeedDates.parse("2024-10-02T08:00:00+09:00"))
        assertEquals(Instant.parse("2024-10-02T08:00:00Z"), FeedDates.parse("2024-10-02T08:00:00Z"))
        assertEquals(Instant.parse("2024-10-02T08:00:00.500Z"), FeedDates.parse("2024-10-02T08:00:00.5Z"))
    }

    @Test
    fun `タイムゾーンの無い ISO 形式を読む`() {
        assertEquals(Instant.parse("2024-10-02T08:00:00Z"), FeedDates.parse("2024-10-02T08:00:00"))
        assertEquals(Instant.parse("2024-10-02T08:00:00Z"), FeedDates.parse("2024-10-02 08:00:00"))
    }

    @Test
    fun `日付だけなら 0 時として読む`() {
        assertEquals(Instant.parse("2024-10-02T00:00:00Z"), FeedDates.parse("2024-10-02"))
    }

    @Test
    fun `前後に空白があっても読む`() {
        assertEquals(Instant.parse("2024-10-02T08:00:00Z"), FeedDates.parse("  2024-10-02T08:00:00Z  "))
    }

    @Test
    fun `読めないものは null にする`() {
        assertNull(FeedDates.parse(null))
        assertNull(FeedDates.parse(""))
        assertNull(FeedDates.parse("   "))
        assertNull(FeedDates.parse("きのう"))
        assertNull(FeedDates.parse("2024/10/02"))
    }
}
