package net.matsudamper.mastodon.rss.repository.sqlite

import net.matsudamper.mastodon.rss.repository.AccountProfile
import net.matsudamper.mastodon.rss.repository.AccountProfileRepository
import net.matsudamper.mastodon.rss.repository.jooq.Tables.ACCOUNT_PROFILES
import org.jooq.DSLContext
import org.jooq.Record3

internal class SqliteAccountProfileRepository(
    private val jooq: SqliteJooq,
) : AccountProfileRepository {
    override fun findByUsername(username: String): AccountProfile? = jooq.transaction { dsl ->
        dsl.selectByUsername(username)
    }

    override fun findByUsernames(usernames: Collection<String>): Map<String, AccountProfile> {
        if (usernames.isEmpty()) return emptyMap()
        return jooq.transaction { dsl ->
            val records = dsl
                .select(
                    ACCOUNT_PROFILES.USERNAME,
                    ACCOUNT_PROFILES.DISPLAY_NAME,
                    ACCOUNT_PROFILES.SUMMARY,
                )
                .from(ACCOUNT_PROFILES)
                .where(ACCOUNT_PROFILES.USERNAME.`in`(usernames))
                .fetch()
                .map { it.toAccountProfile() }

            val map = records.associateBy { it.username.lowercase() }
            usernames.mapNotNull { key ->
                val profile = map[key.lowercase()] ?: return@mapNotNull null
                key to profile
            }.toMap()
        }
    }

    override fun upsert(
        username: String,
        displayName: String,
        summary: String,
    ): AccountProfile = jooq.transaction { dsl ->
        dsl
            .insertInto(ACCOUNT_PROFILES)
            .set(ACCOUNT_PROFILES.USERNAME, username)
            .set(ACCOUNT_PROFILES.DISPLAY_NAME, displayName)
            .set(ACCOUNT_PROFILES.SUMMARY, summary)
            .onConflict(ACCOUNT_PROFILES.USERNAME)
            .doUpdate()
            .set(ACCOUNT_PROFILES.DISPLAY_NAME, displayName)
            .set(ACCOUNT_PROFILES.SUMMARY, summary)
            .execute()

        AccountProfile(username = username, displayName = displayName, summary = summary)
    }

    private fun DSLContext.selectByUsername(username: String): AccountProfile? = select(
        ACCOUNT_PROFILES.USERNAME,
        ACCOUNT_PROFILES.DISPLAY_NAME,
        ACCOUNT_PROFILES.SUMMARY,
    )
        .from(ACCOUNT_PROFILES)
        .where(ACCOUNT_PROFILES.USERNAME.eq(username))
        .fetchOne()
        ?.toAccountProfile()

    private fun Record3<String, String, String>.toAccountProfile(): AccountProfile = AccountProfile(
        username = get(ACCOUNT_PROFILES.USERNAME),
        displayName = get(ACCOUNT_PROFILES.DISPLAY_NAME),
        summary = get(ACCOUNT_PROFILES.SUMMARY),
    )
}
