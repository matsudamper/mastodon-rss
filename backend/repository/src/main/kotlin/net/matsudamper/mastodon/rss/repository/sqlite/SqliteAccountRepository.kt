package net.matsudamper.mastodon.rss.repository.sqlite

import java.time.Instant
import net.matsudamper.mastodon.rss.repository.Account
import net.matsudamper.mastodon.rss.repository.AccountRepository
import net.matsudamper.mastodon.rss.repository.jooq.Tables.ACCOUNTS
import org.jooq.DSLContext
import org.jooq.Record2

internal class SqliteAccountRepository(
    private val jooq: SqliteJooq,
) : AccountRepository {
    override fun list(): List<Account> =
        jooq.transaction { dsl ->
            dsl
                .select(ACCOUNTS.USERNAME, ACCOUNTS.CREATED_AT)
                .from(ACCOUNTS)
                // 時刻で並べると、同じ時刻に入った 2 件の順が実行のたびに変わる
                .orderBy(ACCOUNTS.ID)
                .fetch()
                .map { it.toAccount() }
        }

    override fun findByUsername(username: String): Account? = jooq.transaction { dsl -> dsl.selectByUsername(username) }

    override fun add(
        username: String,
        createdAt: Instant,
    ): Account? =
        jooq.transaction { dsl ->
            // UNIQUE 制約違反を捕まえる形にすると、他の理由で落ちたときと区別が付かない。
            // 書き込みは接続 1 本に直列化されているので、同じトランザクションで
            // 見てから入れれば取りこぼさない
            if (dsl.selectByUsername(username) != null) return@transaction null

            dsl
                .insertInto(ACCOUNTS)
                .set(ACCOUNTS.USERNAME, username)
                .set(ACCOUNTS.CREATED_AT, createdAt.toString())
                .execute()

            Account(username = username, createdAt = createdAt)
        }

    /** 列に COLLATE NOCASE が付いているので、綴りの揺れは SQLite 側で吸収される */
    private fun DSLContext.selectByUsername(username: String): Account? =
        select(ACCOUNTS.USERNAME, ACCOUNTS.CREATED_AT)
            .from(ACCOUNTS)
            .where(ACCOUNTS.USERNAME.eq(username))
            .fetchOne()
            ?.toAccount()

    private fun Record2<String, String>.toAccount(): Account =
        Account(
            username = get(ACCOUNTS.USERNAME),
            createdAt = Instant.parse(get(ACCOUNTS.CREATED_AT)),
        )
}
