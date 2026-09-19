package net.matsudamper.mastodon.rss.repository.sqlite

import java.time.Instant
import net.matsudamper.mastodon.rss.repository.Account
import net.matsudamper.mastodon.rss.repository.AccountDeletion
import net.matsudamper.mastodon.rss.repository.AccountDeletionResult
import net.matsudamper.mastodon.rss.repository.AccountPosition
import net.matsudamper.mastodon.rss.repository.AccountRepository
import net.matsudamper.mastodon.rss.repository.jooq.Tables.ACCOUNTS
import net.matsudamper.mastodon.rss.repository.jooq.Tables.DELIVERY_QUEUE
import net.matsudamper.mastodon.rss.repository.jooq.Tables.FEEDS
import net.matsudamper.mastodon.rss.repository.jooq.Tables.FOLLOWERS
import net.matsudamper.mastodon.rss.repository.jooq.Tables.NOTES
import net.matsudamper.mastodon.rss.repository.sqlite.db.DeliveryKindDbValue
import net.matsudamper.mastodon.rss.shared.AccountId
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL

internal class SqliteAccountRepository(
    private val jooq: SqliteJooq,
) : AccountRepository {
    @Deprecated("ページングに移行する。list(after, limit) を使う")
    override fun list(): List<Account> = jooq.withConnection { dsl ->
        dsl
            .select(ACCOUNT_COLUMNS)
            .from(ACCOUNTS)
            .where(ALIVE)
            // 同じ時刻に入った 2 件は時刻だけでは順が決まらないので id で揃える
            .orderBy(ACCOUNTS.CREATED_AT, ACCOUNTS.ID)
            .fetch()
            .map { it.toAccount() }
    }

    override fun list(after: AccountPosition?, limit: Int): List<Account> = jooq.withConnection { dsl ->
        if (limit <= 0) return@withConnection emptyList()

        dsl
            .select(ACCOUNT_COLUMNS)
            .from(ACCOUNTS)
            .where(ALIVE)
            .and(after?.let { laterThan(it) } ?: DSL.noCondition())
            .orderBy(ACCOUNTS.CREATED_AT.asc(), ACCOUNTS.ID.asc())
            .limit(limit)
            .fetch()
            .map { it.toAccount() }
    }

    /**
     * 並び順で [position] より後ろにあるものを絞る条件。
     *
     * 時刻だけで比べると、同じ時刻のアカウントがページの境目に来たときに落ちるか重複する
     */
    private fun laterThan(position: AccountPosition): Condition {
        val createdAt = StoredInstant.format(position.createdAt)

        return ACCOUNTS.CREATED_AT.gt(createdAt)
            .or(ACCOUNTS.CREATED_AT.eq(createdAt).and(ACCOUNTS.ID.gt(position.id.value)))
    }

    override fun findById(id: AccountId): Account? = jooq.withConnection { dsl ->
        dsl
            .select(ACCOUNT_COLUMNS)
            .from(ACCOUNTS)
            .where(ACCOUNTS.ID.eq(id.value))
            .and(ALIVE)
            .fetchOne()
            ?.toAccount()
    }

    override fun findByUsername(username: String): Account? = jooq.withConnection { dsl -> dsl.selectByUsername(username) }

    override fun findByUsernames(usernames: Collection<String>): Map<String, Account> {
        if (usernames.isEmpty()) return emptyMap()
        return jooq.withConnection { dsl ->
            val records = dsl
                .select(ACCOUNT_COLUMNS)
                .from(ACCOUNTS)
                .where(ACCOUNTS.USERNAME.`in`(usernames))
                .and(ALIVE)
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
        // UNIQUE 制約違反を捕まえる形にすると、他の理由で落ちたときと区別が付かない。
        // 書き込みは接続 1 本に直列化されているので、同じトランザクションで
        // 見てから入れれば取りこぼさない。消した行も名前を押さえているので、
        // 作り直せない。作り直せると、送り残した Delete{Actor} が
        // 新しいアカウントのものとして配られる
        if (dsl.fetchExists(DSL.selectOne().from(ACCOUNTS).where(ACCOUNTS.USERNAME.eq(username)))) return@transaction null

        val id = dsl
            .insertInto(ACCOUNTS)
            .set(ACCOUNTS.USERNAME, username)
            .set(ACCOUNTS.CREATED_AT, StoredInstant.format(createdAt))
            .returning(ACCOUNTS.ID)
            .fetchOne()
            ?.get(ACCOUNTS.ID)
            ?: return@transaction null

        Account(
            id = AccountId(id),
            username = username,
            createdAt = createdAt,
            displayName = null,
            summary = null,
            deletedAt = null,
        )
    }

    override fun updateProfile(
        id: AccountId,
        displayName: String?,
        summary: String?,
    ): Account? = jooq.transaction { dsl ->
        val updated = dsl
            .update(ACCOUNTS)
            .set(ACCOUNTS.DISPLAY_NAME, displayName)
            .set(ACCOUNTS.SUMMARY, summary)
            .where(ACCOUNTS.ID.eq(id.value))
            .and(ALIVE)
            .execute()

        if (updated == 0) return@transaction null

        dsl
            .select(ACCOUNT_COLUMNS)
            .from(ACCOUNTS)
            .where(ACCOUNTS.ID.eq(id.value))
            .fetchOne()
            ?.toAccount()
    }

    override fun findDeletedByUsername(username: String): Account? = jooq.withConnection { dsl ->
        dsl
            .select(ACCOUNT_COLUMNS)
            .from(ACCOUNTS)
            .where(ACCOUNTS.USERNAME.eq(username))
            .and(ACCOUNTS.DELETED_AT.isNotNull)
            .fetchOne()
            ?.toAccount()
    }

    /**
     * 消えるものを全部消してから投函する。先に投函すると、この後で消す
     * 「そのアカウントのまだ送っていない配信」に今入れた行まで含まれる
     */
    override fun markDeleted(deletion: AccountDeletion): AccountDeletionResult? = jooq.transaction { dsl ->
        val deletedAt = StoredInstant.format(deletion.deletedAt)

        val marked = dsl
            .update(ACCOUNTS)
            .set(ACCOUNTS.DELETED_AT, deletedAt)
            .where(ACCOUNTS.ID.eq(deletion.id.value))
            .and(ALIVE)
            .execute()
        // 同時に呼ばれても、消せた 1 つだけが配信を投函する
        if (marked == 0) return@transaction null

        // フィードと記事は外部キーで消える。投稿とフォロワーは accounts を
        // 参照していないので名前で消す
        dsl.deleteFrom(FEEDS).where(FEEDS.ACCOUNT_ID.eq(deletion.id.value)).execute()

        val deletedNotes = dsl
            .deleteFrom(NOTES)
            .where(NOTES.USERNAME.eq(deletion.username))
            .execute()

        val removedFollowers = dsl
            .deleteFrom(FOLLOWERS)
            .where(FOLLOWERS.USERNAME.eq(deletion.username))
            .execute()

        dsl
            .deleteFrom(DELIVERY_QUEUE)
            .where(DELIVERY_QUEUE.USERNAME.eq(deletion.username))
            .execute()

        deletion.inboxes.forEach { inbox ->
            DeliveryQueueRows.insertPending(
                dsl = dsl,
                kind = DeliveryKindDbValue.DELETE_ACTOR,
                username = deletion.username,
                inbox = inbox,
                body = deletion.body,
                enqueuedAt = deletion.deletedAt,
                notePublicId = null,
                targetActorUri = null,
            )
        }

        AccountDeletionResult(
            deletedNotes = deletedNotes,
            removedFollowers = removedFollowers,
            deliveries = deletion.inboxes.size,
        )
    }

    override fun purgeDeleted(): Int = jooq.transaction { dsl ->
        dsl
            .deleteFrom(ACCOUNTS)
            .where(ACCOUNTS.DELETED_AT.isNotNull)
            .andNotExists(
                DSL
                    .selectOne()
                    .from(DELIVERY_QUEUE)
                    .where(DELIVERY_QUEUE.USERNAME.eq(ACCOUNTS.USERNAME)),
            )
            .execute()
    }

    /**
     * 列に COLLATE NOCASE が付いているので、綴りの揺れは SQLite 側で吸収される
     */
    private fun DSLContext.selectByUsername(username: String): Account? = select(ACCOUNT_COLUMNS)
        .from(ACCOUNTS)
        .where(ACCOUNTS.USERNAME.eq(username))
        .and(ALIVE)
        .fetchOne()
        ?.toAccount()

    private fun Record.toAccount(): Account = Account(
        id = AccountId(get(ACCOUNTS.ID)),
        username = get(ACCOUNTS.USERNAME),
        createdAt = StoredInstant.parse(get(ACCOUNTS.CREATED_AT)),
        displayName = get(ACCOUNTS.DISPLAY_NAME),
        summary = get(ACCOUNTS.SUMMARY),
        deletedAt = get(ACCOUNTS.DELETED_AT)?.let { StoredInstant.parse(it) },
    )

    private companion object {
        val ACCOUNT_COLUMNS = listOf(
            ACCOUNTS.ID,
            ACCOUNTS.USERNAME,
            ACCOUNTS.CREATED_AT,
            ACCOUNTS.DISPLAY_NAME,
            ACCOUNTS.SUMMARY,
            ACCOUNTS.DELETED_AT,
        )

        /**
         * 消していないアカウントだけを見る条件。消した行は名前を押さえるためだけに残る
         */
        val ALIVE: Condition = ACCOUNTS.DELETED_AT.isNull
    }
}
