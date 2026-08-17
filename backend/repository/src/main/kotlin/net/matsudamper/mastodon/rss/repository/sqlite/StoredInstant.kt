package net.matsudamper.mastodon.rss.repository.sqlite

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder

/**
 * 時刻を TEXT に入れるときの書式。
 *
 * 秒未満の桁を固定して書く。`Instant.toString()` は末尾の 0 を落とすので、
 * そのまま入れると文字列の並びが時刻の並びと一致しない
 * （`00:00:00Z` が `00:00:00.5Z` より後ろに来る）。
 * 並び替えと範囲の比較を SQL に任せている以上、揃っていないと結果が狂う。
 */
internal object StoredInstant {
    private val FORMAT: DateTimeFormatter = DateTimeFormatterBuilder().appendInstant(9).toFormatter()

    fun format(value: Instant): String = FORMAT.format(value)

    fun parse(value: String): Instant = Instant.parse(value)
}
