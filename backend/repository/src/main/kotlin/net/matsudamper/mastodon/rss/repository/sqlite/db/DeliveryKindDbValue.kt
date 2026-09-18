package net.matsudamper.mastodon.rss.repository.sqlite.db

import net.matsudamper.mastodon.rss.repository.DeliveryKind

internal enum class DeliveryKindDbValue(
    internal val dbValue: String,
) {
    CREATE_NOTE("create_note"),
    ACCEPT_FOLLOW("accept_follow"),
    DELETE_NOTE("delete_note"),
    DELETE_ACTOR("delete_actor"),
    UPDATE_ACTOR("update_actor"),
    ;

    fun toDeliveryKind(): DeliveryKind =
        when (this) {
            CREATE_NOTE -> DeliveryKind.CREATE_NOTE
            ACCEPT_FOLLOW -> DeliveryKind.ACCEPT_FOLLOW
            DELETE_NOTE -> DeliveryKind.DELETE_NOTE
            DELETE_ACTOR -> DeliveryKind.DELETE_ACTOR
            UPDATE_ACTOR -> DeliveryKind.UPDATE_ACTOR
        }

    companion object {
        fun of(kind: DeliveryKind): DeliveryKindDbValue =
            when (kind) {
                DeliveryKind.CREATE_NOTE -> CREATE_NOTE
                DeliveryKind.ACCEPT_FOLLOW -> ACCEPT_FOLLOW
                DeliveryKind.DELETE_NOTE -> DELETE_NOTE
                DeliveryKind.DELETE_ACTOR -> DELETE_ACTOR
                DeliveryKind.UPDATE_ACTOR -> UPDATE_ACTOR
            }

        fun parse(value: String): DeliveryKindDbValue =
            entries.find { it.dbValue == value }
                ?: error("未知の配信の種別: $value")
    }
}
