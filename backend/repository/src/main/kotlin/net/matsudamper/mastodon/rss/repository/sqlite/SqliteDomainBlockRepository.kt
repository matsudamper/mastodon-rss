package net.matsudamper.mastodon.rss.repository.sqlite

import java.time.Instant
import net.matsudamper.mastodon.rss.repository.DomainBlock
import net.matsudamper.mastodon.rss.repository.DomainBlockRepository
import net.matsudamper.mastodon.rss.repository.jooq.Tables.DOMAIN_BLOCKS
import net.matsudamper.mastodon.rss.repository.sqlite.db.DomainBlockReasonDbValue
import org.jooq.Record
import org.jooq.impl.DSL

internal class SqliteDomainBlockRepository(
    private val jooq: SqliteJooq,
) : DomainBlockRepository {
    override fun blocksDelivery(domain: String): Boolean = jooq.withConnection { dsl ->
        DomainBlockRows.blocksDelivery(dsl = dsl, domain = domain)
    }

    override fun blocksInbox(domain: String): Boolean = jooq.withConnection { dsl ->
        DomainBlockRows.blocksInbox(dsl = dsl, domain = domain)
    }

    override fun markUnavailable(
        domain: String,
        description: String,
        at: Instant,
    ): Boolean = jooq.transaction { dsl ->
        val inserted = dsl
            .insertInto(DOMAIN_BLOCKS)
            .set(DOMAIN_BLOCKS.DOMAIN, domain)
            .set(DOMAIN_BLOCKS.REASON, DomainBlockReasonDbValue.UNAVAILABLE.dbValue)
            .set(DOMAIN_BLOCKS.REASON_DESCRIPTION, description)
            // 送れない相手からの受信まで止めると、戻ってきたことを知る手立てが減る
            .set(DOMAIN_BLOCKS.BLOCK_DELIVERY, 1L)
            .set(DOMAIN_BLOCKS.BLOCK_INBOX, 0L)
            .set(DOMAIN_BLOCKS.CREATED_AT, StoredInstant.format(at))
            .onConflict(DOMAIN_BLOCKS.DOMAIN)
            .doNothing()
            .execute()

        inserted > 0
    }

    override fun clearUnavailable(domain: String): Boolean {
        // 配信の成功と受信のたびに呼ばれる。止めていないときまで書き込みのロックを取らない
        val unavailable = jooq.withConnection { dsl ->
            dsl.fetchExists(
                DSL
                    .selectOne()
                    .from(DOMAIN_BLOCKS)
                    .where(DOMAIN_BLOCKS.DOMAIN.eq(domain))
                    .and(DOMAIN_BLOCKS.REASON.eq(DomainBlockReasonDbValue.UNAVAILABLE.dbValue)),
            )
        }
        if (!unavailable) return false

        return jooq.transaction { dsl ->
            // 確かめてから消すまでに手で止め直されていたら、そちらを残す
            dsl
                .deleteFrom(DOMAIN_BLOCKS)
                .where(DOMAIN_BLOCKS.DOMAIN.eq(domain))
                .and(DOMAIN_BLOCKS.REASON.eq(DomainBlockReasonDbValue.UNAVAILABLE.dbValue))
                .execute() > 0
        }
    }

    override fun find(domain: String): DomainBlock? = jooq.withConnection { dsl ->
        dsl
            .selectFrom(DOMAIN_BLOCKS)
            .where(DOMAIN_BLOCKS.DOMAIN.eq(domain))
            .fetchOne()
            ?.toDomainBlock()
    }

    override fun list(
        afterDomain: String?,
        limit: Int,
    ): List<DomainBlock> {
        if (limit <= 0) return listOf()

        return jooq.withConnection { dsl ->
            dsl
                .selectFrom(DOMAIN_BLOCKS)
                .where(afterDomain?.let { DOMAIN_BLOCKS.DOMAIN.gt(it) } ?: DSL.noCondition())
                .orderBy(DOMAIN_BLOCKS.DOMAIN.asc())
                .limit(limit)
                .fetch()
                .map { it.toDomainBlock() }
        }
    }

    override fun saveManual(
        domain: String,
        blockDelivery: Boolean,
        blockInbox: Boolean,
        description: String?,
        at: Instant,
    ): DomainBlock = jooq.transaction { dsl ->
        val reason = DomainBlockReasonDbValue.MANUAL.dbValue
        val blockDeliveryValue = if (blockDelivery) 1L else 0L
        val blockInboxValue = if (blockInbox) 1L else 0L

        dsl
            .insertInto(DOMAIN_BLOCKS)
            .set(DOMAIN_BLOCKS.DOMAIN, domain)
            .set(DOMAIN_BLOCKS.REASON, reason)
            .set(DOMAIN_BLOCKS.REASON_DESCRIPTION, description)
            .set(DOMAIN_BLOCKS.BLOCK_DELIVERY, blockDeliveryValue)
            .set(DOMAIN_BLOCKS.BLOCK_INBOX, blockInboxValue)
            .set(DOMAIN_BLOCKS.CREATED_AT, StoredInstant.format(at))
            .onConflict(DOMAIN_BLOCKS.DOMAIN)
            .doUpdate()
            .set(DOMAIN_BLOCKS.REASON, reason)
            .set(DOMAIN_BLOCKS.REASON_DESCRIPTION, description)
            .set(DOMAIN_BLOCKS.BLOCK_DELIVERY, blockDeliveryValue)
            .set(DOMAIN_BLOCKS.BLOCK_INBOX, blockInboxValue)
            .execute()

        checkNotNull(
            dsl
                .selectFrom(DOMAIN_BLOCKS)
                .where(DOMAIN_BLOCKS.DOMAIN.eq(domain))
                .fetchOne()
                ?.toDomainBlock(),
        ) { "止めたドメインの行を作れなかった: $domain" }
    }

    override fun delete(domain: String): Boolean = jooq.transaction { dsl ->
        dsl
            .deleteFrom(DOMAIN_BLOCKS)
            .where(DOMAIN_BLOCKS.DOMAIN.eq(domain))
            .execute() > 0
    }

    private fun Record.toDomainBlock(): DomainBlock = DomainBlock(
        domain = get(DOMAIN_BLOCKS.DOMAIN),
        reason = DomainBlockReasonDbValue.parse(get(DOMAIN_BLOCKS.REASON)).toDomainBlockReason(),
        reasonDescription = get(DOMAIN_BLOCKS.REASON_DESCRIPTION),
        blockDelivery = get(DOMAIN_BLOCKS.BLOCK_DELIVERY) == 1L,
        blockInbox = get(DOMAIN_BLOCKS.BLOCK_INBOX) == 1L,
        createdAt = StoredInstant.parse(get(DOMAIN_BLOCKS.CREATED_AT)),
    )
}
