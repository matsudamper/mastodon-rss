package net.matsudamper.mastodon.rss.repository.sqlite

import java.time.Instant
import net.matsudamper.mastodon.rss.repository.jooq.Tables.DELIVERY_QUEUE
import net.matsudamper.mastodon.rss.repository.sqlite.db.DeliveryKindDbValue
import net.matsudamper.mastodon.rss.repository.sqlite.db.DeliveryStateDbValue
import org.jooq.Condition
import org.jooq.DSLContext

/**
 * `delivery_queue` への投函。
 *
 * 投函は配信キュー以外のリポジトリからも、記録と同じトランザクションで行う。
 * 行の作り方をそれぞれに持たせると、初回に送る時刻や状態の入れ方が片方だけずれる。
 */
internal object DeliveryQueueRows {
    /**
     * 送る時刻を待つ行を 1 つ入れる。最初の 1 回は [enqueuedAt] にすぐ送る。
     *
     * @param notePublicId この行が配る投稿。投稿を伴わない種別では null
     * @param targetActorUri この行が相手にする向こうのアクター。関係しない種別では null
     */
    fun insertPending(
        dsl: DSLContext,
        kind: DeliveryKindDbValue,
        username: String,
        inbox: String,
        body: String,
        enqueuedAt: Instant,
        notePublicId: String?,
        targetActorUri: String?,
    ) {
        val enqueuedAtText = StoredInstant.format(enqueuedAt)

        dsl
            .insertInto(DELIVERY_QUEUE)
            .set(DELIVERY_QUEUE.KIND, kind.dbValue)
            .set(DELIVERY_QUEUE.USERNAME, username)
            .set(DELIVERY_QUEUE.INBOX, inbox)
            .set(DELIVERY_QUEUE.INBOX_HOST, InboxHost.of(inbox))
            .set(DELIVERY_QUEUE.BODY, body)
            .set(DELIVERY_QUEUE.STATE, DeliveryStateDbValue.PENDING.dbValue)
            .set(DELIVERY_QUEUE.ATTEMPTS, 0L)
            .set(DELIVERY_QUEUE.NEXT_ATTEMPT_AT, enqueuedAtText)
            .set(DELIVERY_QUEUE.ENQUEUED_AT, enqueuedAtText)
            .set(DELIVERY_QUEUE.LAST_ERROR, null as String?)
            .set(DELIVERY_QUEUE.NOTE_PUBLIC_ID, notePublicId)
            .set(DELIVERY_QUEUE.TARGET_ACTOR_URI, targetActorUri)
            .execute()
    }

    /**
     * その相手への、送り終えていない `Accept` を消す。
     *
     * 送っている最中の行も消す。残すと、送れなかったときに送り直し待ちに戻って、
     * 相手が居なくなった後も諦めるまで送り続ける。消えた行の結果は記録できなくなるが、
     * [DeliveryQueueRepository.markDelivered] も送り直しの記録も、行が無い場合を許容する。
     *
     * @return 消えた件数
     */
    fun deletePendingAccept(
        dsl: DSLContext,
        username: String,
        followerActorUri: String,
    ): Int = dsl
        .deleteFrom(DELIVERY_QUEUE)
        .where(unsentAccept())
        .and(DELIVERY_QUEUE.USERNAME.eq(username))
        .and(DELIVERY_QUEUE.TARGET_ACTOR_URI.eq(followerActorUri))
        .execute()

    /**
     * その相手への、送り終えていない `Accept` を全部消す。相手が消えたときに使う。
     *
     * こちらのどのアカウントへのフォローだったかは問わない。相手が消えた以上、
     * どのアカウントの `Accept` も届ける先が無い。
     *
     * @return 消えた件数
     */
    fun deletePendingAcceptsToActor(
        dsl: DSLContext,
        followerActorUri: String,
    ): Int = dsl
        .deleteFrom(DELIVERY_QUEUE)
        .where(unsentAccept())
        .and(DELIVERY_QUEUE.TARGET_ACTOR_URI.eq(followerActorUri))
        .execute()

    /**
     * そのアカウントの、送り終えていない `Accept` を全部消す。
     * フォローをまとめて消すときに使う。
     *
     * @return 消えた件数
     */
    fun deletePendingAcceptsOfAccount(
        dsl: DSLContext,
        username: String,
    ): Int = dsl
        .deleteFrom(DELIVERY_QUEUE)
        .where(unsentAccept())
        .and(DELIVERY_QUEUE.USERNAME.eq(username))
        .execute()

    /**
     * まだ送り終えていない `Accept`。諦めた行は残す。送れなかった記録まで消す理由が無い
     */
    private fun unsentAccept(): Condition = DELIVERY_QUEUE.KIND.eq(DeliveryKindDbValue.ACCEPT_FOLLOW.dbValue)
        .and(DELIVERY_QUEUE.STATE.`in`(DeliveryStateDbValue.PENDING.dbValue, DeliveryStateDbValue.DELIVERING.dbValue))
}
