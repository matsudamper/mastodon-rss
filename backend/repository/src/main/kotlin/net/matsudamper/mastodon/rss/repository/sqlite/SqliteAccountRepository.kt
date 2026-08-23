package net.matsudamper.mastodon.rss.repository.sqlite // pragma: allowlist secret

import java.time.Instant
import net.matsudamper.mastodon.rss.repository.Account // pragma: allowlist secret
import net.matsudamper.mastodon.rss.repository.AccountId // pragma: allowlist secret
import net.matsudamper.mastodon.rss.repository.AccountRepository // pragma: allowlist secret
import net.matsudamper.mastodon.rss.repository.jooq.Tables.ACCOUNTS // pragma: allowlist secret
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL

internal class SqliteAccountRepository(
    private val jooq: SqliteJooq,
) : AccountRepository {
    @Deprecated("ページングに移行する。list(afterUsername, limit) を使う")
    override fun list(): List<Account> = jooq.transaction { dsl ->
        dsl
            .select(ACCOUNTS.ID, ACCOUNTS.USERNAME, ACCOUNTS.CREATED_AT)
            .from(ACCOUNTS)
            .orderBy(ACCOUNTS.CREATED_AT, ACCOUNTS.ID)
            .fetch()
            .map { it.toAccount() }
    }

    override fun list(afterUsername: String?, limit: Int): List<Account> = jooq.transaction { dsl ->
        if (limit <= 0) return@transaction emptyList()

        val after = if (afterUsername == null) {
            DSL.noCondition()
        } else {
            val afterRecord = dsl
                .select(ACCOUNTS.ID, ACCOUNTS.CREATED_AT)
                .from(ACCOUNTS)
                .where(ACCOUNTS.USERNAME.eq(afterUsername))
                .fetchOne() ?: return@transaction emptyList()

            val afterId = afterRecord.get(ACCOUNTS.ID)
            val afterCreatedAt = afterRecord.get(ACCOUNTS.CREATED_AT)

            ACCOUNTS.CREATED_AT.gt(afterCreatedAt)
                .or(ACCOUNTS.CREATED_AT.eq(afterCreatedAt).and(ACCOUNTS.ID.gt(afterId)))
        }

        dsl
            .select(ACCOUNTS.ID, ACCOUNTS.USERNAME, ACCOUNTS.CREATED_AT)
            .from(ACCOUNTS)
            .where(after)
            .orderBy(ACCOUNTS.CREATED_AT.asc(), ACCOUNTS.ID.asc())
            .limit(limit)
            .fetch()
            .map { it.toAccount() }
    }

    override fun findById(id: AccountId): Account? = jooq.transaction { dsl ->
        dsl
            .select(ACCOUNTS.ID, ACCOUNTS.USERNAME, ACCOUNTS.CREATED_AT)
            .from(ACCOUNTS)
            .where(ACCOUNTS.ID.eq(id.value.toInt()))
            .fetchOne()
            ?.toAccount()
    }

    override fun findByUsername(username: String): Account? = jooq.transaction { dsl -> dsl.selectByUsername(username) }

    override fun findByUsernames(usernames: Collection<String>): Map<String, Account> {
        if (usernames.isEmpty()) return emptyMap()
        return jooq.transaction { dsl ->
            val records = dsl
                .select(ACCOUNTS.ID, ACCOUNTS.USERNAME, ACCOUNTS.CREATED_AT)
                .from(ACCOUNTS)
                .where(ACCOUNTS.USERNAME.`in`(usernames))
                .fetch()
                .map { it.toAccount() }

            val map = records.associateBy { it.username.lowercase() }
            usernames.mapNotNull { key ->
                val account = map[key.lowercase()] ?: return@mapNotNull null
                key to account
            }.toMap()
        }
    }

    override fun add(
        username: String,
        createdAt: Instant,
    ): Account? = jooq.transaction { dsl ->
        if (dsl.selectByUsername(username) != null) return@transaction null

        val id = dsl
            .insertInto(ACCOUNTS)
            .set(ACCOUNTS.USERNAME, username)
            .set(ACCOUNTS.CREATED_AT, StoredInstant.format(createdAt))
            .returning(ACCOUNTS.ID)
            .fetchOne()
            ?.get(ACCOUNTS.ID)
            ?: return@transaction null

        Account(id = AccountId(id.toLong()), username = username, createdAt = createdAt)
    }

    private fun DSLContext.selectByUsername(username: String): Account? = select(ACCOUNTS.ID, ACCOUNTS.USERNAME, ACCOUNTS.CREATED_AT)
        .from(ACCOUNTS)
        .where(ACCOUNTS.USERNAME.eq(username))
        .fetchOne()
        ?.toAccount()

    private fun Record.toAccount(): Account = Account(
        id = AccountId(get(ACCOUNTS.ID).toLong()),
        username = get(ACCOUNTS.USERNAME),
        createdAt = StoredInstant.parse(get(ACCOUNTS.CREATED_AT)),
    )
}
