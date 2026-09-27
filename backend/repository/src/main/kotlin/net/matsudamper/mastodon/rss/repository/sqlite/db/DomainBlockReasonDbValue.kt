package net.matsudamper.mastodon.rss.repository.sqlite.db

import net.matsudamper.mastodon.rss.repository.DomainBlockReason

internal enum class DomainBlockReasonDbValue(
    internal val dbValue: String,
) {
    UNAVAILABLE("unavailable"),
    MANUAL("manual"),
    ;

    fun toDomainBlockReason(): DomainBlockReason =
        when (this) {
            UNAVAILABLE -> DomainBlockReason.UNAVAILABLE
            MANUAL -> DomainBlockReason.MANUAL
        }

    companion object {
        fun parse(value: String): DomainBlockReasonDbValue =
            entries.find { it.dbValue == value }
                ?: error("未知のドメインを止めた理由: $value")
    }
}
