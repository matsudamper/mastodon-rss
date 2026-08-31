@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package net.matsudamper.mastodon.rss.frontend.format

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object UnixTimeUtil {
    /**
     * エポックからの秒数を、画面に出す文字列にする。
     *
     * 秒数のままではどの時点なのか読めないので、必ずここを通してから画面に載せる。
     * 書式とタイムゾーンは見ている人の環境に合わせる。
     */
    fun format(epochSeconds: Long): String {
        val dateTime = Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(TimeZone.currentSystemDefault())
        val date = listOf(
            dateTime.year.toString().padStart(4, '0'),
            (dateTime.month.ordinal + 1).toString().padStart(2, '0'),
            dateTime.day.toString().padStart(2, '0'),
        ).joinToString("-")
        return "$date ${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"
    }
}
