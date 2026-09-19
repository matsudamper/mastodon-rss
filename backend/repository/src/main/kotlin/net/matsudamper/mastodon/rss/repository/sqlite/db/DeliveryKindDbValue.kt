package net.matsudamper.mastodon.rss.repository.sqlite.db

import net.matsudamper.mastodon.rss.repository.DeliveryKind

internal enum class DeliveryKindDbValue(
    internal val dbValue: String,
) {
    CREATE_NOTE("create_note"),
    ;

    fun toDeliveryKind(): DeliveryKind =
        when (this) {
            CREATE_NOTE -> DeliveryKind.CREATE_NOTE
        }

    companion object {
        fun of(kind: DeliveryKind): DeliveryKindDbValue =
            when (kind) {
                DeliveryKind.CREATE_NOTE -> CREATE_NOTE
            }

        fun parse(value: String): DeliveryKindDbValue =
            entries.find { it.dbValue == value }
                ?: error("未知の配信の種別: $value")
    }
}
