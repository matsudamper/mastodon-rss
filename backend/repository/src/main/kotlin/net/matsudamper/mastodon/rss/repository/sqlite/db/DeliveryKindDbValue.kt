package net.matsudamper.mastodon.rss.repository.sqlite.db

import net.matsudamper.mastodon.rss.repository.DeliveryKind

internal enum class DeliveryKindDbValue(
    internal val dbValue: String,
) {
    CREATE_NOTE("create_note"),
    DELETE_NOTE("delete_note"),
    UPDATE_ACTOR("update_actor"),
    DELETE_ACTOR("delete_actor"),
    ACCEPT_FOLLOW("accept_follow"),
    ;

    fun toDeliveryKind(): DeliveryKind =
        when (this) {
            CREATE_NOTE -> DeliveryKind.CREATE_NOTE
            DELETE_NOTE -> DeliveryKind.DELETE_NOTE
            UPDATE_ACTOR -> DeliveryKind.UPDATE_ACTOR
            DELETE_ACTOR -> DeliveryKind.DELETE_ACTOR
            ACCEPT_FOLLOW -> DeliveryKind.ACCEPT_FOLLOW
        }

    companion object {
        fun of(kind: DeliveryKind): DeliveryKindDbValue =
            when (kind) {
                DeliveryKind.CREATE_NOTE -> CREATE_NOTE
                DeliveryKind.DELETE_NOTE -> DELETE_NOTE
                DeliveryKind.UPDATE_ACTOR -> UPDATE_ACTOR
                DeliveryKind.DELETE_ACTOR -> DELETE_ACTOR
                DeliveryKind.ACCEPT_FOLLOW -> ACCEPT_FOLLOW
            }

        fun parse(value: String): DeliveryKindDbValue =
            entries.find { it.dbValue == value }
                ?: error("未知の配信の種別: $value")
    }
}
