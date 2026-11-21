package net.matsudamper.mastodon.rss.repository.sqlite

import net.matsudamper.mastodon.rss.repository.ServerSettingRepository
import net.matsudamper.mastodon.rss.repository.jooq.Tables.SERVER_SETTINGS

internal class SqliteServerSettingRepository(
    private val jooq: SqliteJooq,
) : ServerSettingRepository {
    override fun maxDeliveryTargetsPerHost(): Int? = jooq.withConnection { dsl ->
        dsl
            .select(SERVER_SETTINGS.MAX_DELIVERY_TARGETS_PER_HOST)
            .from(SERVER_SETTINGS)
            .where(SERVER_SETTINGS.ID.eq(SETTINGS_ROW_ID))
            .fetchOne(SERVER_SETTINGS.MAX_DELIVERY_TARGETS_PER_HOST)
            ?.toInt()
    }

    override fun saveMaxDeliveryTargetsPerHost(value: Int) {
        jooq.withConnection { dsl ->
            dsl
                .insertInto(SERVER_SETTINGS)
                .set(SERVER_SETTINGS.ID, SETTINGS_ROW_ID)
                .set(SERVER_SETTINGS.MAX_DELIVERY_TARGETS_PER_HOST, value.toLong())
                .onConflict(SERVER_SETTINGS.ID)
                .doUpdate()
                .set(SERVER_SETTINGS.MAX_DELIVERY_TARGETS_PER_HOST, value.toLong())
                .execute()
        }
    }

    private companion object {
        /**
         * 行は 1 件だけ。スキーマの CHECK もこの値に固定している
         */
        const val SETTINGS_ROW_ID = 1L
    }
}
