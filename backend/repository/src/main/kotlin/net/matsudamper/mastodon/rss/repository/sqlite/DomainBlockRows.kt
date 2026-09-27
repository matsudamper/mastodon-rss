package net.matsudamper.mastodon.rss.repository.sqlite

import net.matsudamper.mastodon.rss.repository.jooq.Tables.DOMAIN_BLOCKS
import org.jooq.DSLContext
import org.jooq.impl.DSL

/**
 * `domain_blocks` の読み取り。
 *
 * 投函は配信キュー以外のリポジトリからも行うので、配信を止めているかの判定を
 * それぞれに持たせずここに置く。
 */
internal object DomainBlockRows {
    /**
     * そのドメインへの配信を止めているか
     *
     * @param domain 小文字に揃えたホスト名
     */
    fun blocksDelivery(
        dsl: DSLContext,
        domain: String,
    ): Boolean = dsl.fetchExists(
        DSL
            .selectOne()
            .from(DOMAIN_BLOCKS)
            .where(DOMAIN_BLOCKS.DOMAIN.eq(domain))
            .and(DOMAIN_BLOCKS.BLOCK_DELIVERY.eq(1L)),
    )

    /**
     * そのドメインからの受信を止めているか
     *
     * @param domain 小文字に揃えたホスト名
     */
    fun blocksInbox(
        dsl: DSLContext,
        domain: String,
    ): Boolean = dsl.fetchExists(
        DSL
            .selectOne()
            .from(DOMAIN_BLOCKS)
            .where(DOMAIN_BLOCKS.DOMAIN.eq(domain))
            .and(DOMAIN_BLOCKS.BLOCK_INBOX.eq(1L)),
    )
}
