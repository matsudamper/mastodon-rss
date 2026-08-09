package net.matsudamper.mastodon.rss.feed

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/**
 * フィードの日時を [Instant] にする。
 *
 * 形式は仕様上 2 つある。RSS 2.0 の `pubDate` は RFC 822（`Wed, 02 Oct 2024 08:00:00 +0900`）、
 * Atom の `updated` と RSS 1.0 の `dc:date` は RFC 3339（`2024-10-02T08:00:00+09:00`）。
 * ただし配信元の実装はどちらも仕様どおりとは限らないので、読めそうな形を順に試す。
 *
 * 読めなければ null にする。ここで例外を投げると、日時が 1 件おかしいだけで
 * フィード全体が取り込めなくなる。日時は投稿の並び順に使うだけで、
 * 無くても記事は流せる。
 */
object FeedDates {
    fun parse(raw: String?): Instant? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val normalized = replaceZoneAbbreviation(value)

        for (attempt in ATTEMPTS) {
            val instant = runCatching { attempt(normalized) }.getOrNull()
            if (instant != null) return instant
        }
        return null
    }

    /**
     * 試す順。上から順に、最初に読めたものを採る。
     *
     * タイムゾーンを持たない形は UTC として読む。ずれても数時間で、
     * 記事を落とすよりは良い。
     */
    private val ATTEMPTS: List<(String) -> Instant> =
        listOf(
            { OffsetDateTime.parse(it).toInstant() },
            { ZonedDateTime.parse(it).toInstant() },
            { LocalDateTime.parse(it).toInstant(ZoneOffset.UTC) },
            { LocalDate.parse(it).atStartOfDay().toInstant(ZoneOffset.UTC) },
            { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() },
            { LocalDateTime.parse(it, RFC_822_WITHOUT_ZONE).toInstant(ZoneOffset.UTC) },
            { LocalDateTime.parse(it, SPACE_SEPARATED).toInstant(ZoneOffset.UTC) },
        )

    /**
     * タイムゾーンが欠けている RFC 822。
     *
     * `Wed, 02 Oct 2024 08:00:00` のように末尾が無いものが実際にある。
     * 曜日と秒も省略されることがあるので任意にしてある。
     */
    private val RFC_822_WITHOUT_ZONE: DateTimeFormatter =
        DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .parseLenient()
            .optionalStart()
            .appendPattern("EEE")
            .optionalStart()
            .appendLiteral(',')
            .optionalEnd()
            .appendLiteral(' ')
            .optionalEnd()
            .appendPattern("d MMM yyyy HH:mm[:ss]")
            .toFormatter(Locale.ENGLISH)

    /** `2024-10-02 08:00:00`。T の代わりに空白を入れる配信元がある */
    private val SPACE_SEPARATED: DateTimeFormatter =
        DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("yyyy-MM-dd HH:mm[:ss]")
            .toFormatter(Locale.ENGLISH)

    /**
     * 略称のタイムゾーンを、`DateTimeFormatter` が読める形に置き換える。
     *
     * `DateTimeFormatter.RFC_1123_DATE_TIME` が受け付けるのは数値のオフセットと
     * `GMT` だけで、RFC 822 が許している `EST` のような略称は読めない。
     *
     * 表に無い略称は `GMT` にする。RFC 5322 も未知の略称は -0000（＝ UTC）として
     * 扱うと決めている。数時間ずれる可能性はあるが、日時ごと落とすよりは良い。
     */
    private fun replaceZoneAbbreviation(value: String): String {
        val lastSpace = value.lastIndexOf(' ')
        if (lastSpace < 0) return value

        val zone = value.substring(lastSpace + 1)
        if (zone.isEmpty() || zone.length > MAX_ZONE_ABBREVIATION_LENGTH) return value
        if (!zone.all { it.isLetter() }) return value

        val replacement = ZONE_ABBREVIATIONS[zone.lowercase()] ?: "GMT"
        return value.substring(0, lastSpace + 1) + replacement
    }

    /** 略称の長さの上限。これを超えるものはタイムゾーンではないとみなす */
    private const val MAX_ZONE_ABBREVIATION_LENGTH = 5

    /**
     * RFC 822 が挙げている略称と、日本語圏の配信元で見かける `JST`。
     */
    private val ZONE_ABBREVIATIONS: Map<String, String> =
        mapOf(
            "ut" to "GMT",
            "utc" to "GMT",
            "gmt" to "GMT",
            "z" to "GMT",
            "est" to "-0500",
            "edt" to "-0400",
            "cst" to "-0600",
            "cdt" to "-0500",
            "mst" to "-0700",
            "mdt" to "-0600",
            "pst" to "-0800",
            "pdt" to "-0700",
            "jst" to "+0900",
        )
}
