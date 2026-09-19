package net.matsudamper.mastodon.rss.repository.sqlite

import java.time.Instant
import net.matsudamper.mastodon.rss.repository.jooq.Tables.DELIVERY_QUEUE
import net.matsudamper.mastodon.rss.repository.sqlite.db.DeliveryKindDbValue
import net.matsudamper.mastodon.rss.repository.sqlite.db.DeliveryStateDbValue
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
     * その相手への、まだ送っていない `Accept` を消す。
     *
     * 送っている最中の行は消さない。消しても相手には届くので、
     * 消えた行の結果を記録できなくなるだけになる。
     *
     * @return 消えた件数
     */
    fun deletePendingAccept(
        dsl: DSLContext,
        username: String,
        followerActorUri: String,
    ): Int = dsl
        .deleteFrom(DELIVERY_QUEUE)
        .where(DELIVERY_QUEUE.KIND.eq(DeliveryKindDbValue.ACCEPT_FOLLOW.dbValue))
        .and(DELIVERY_QUEUE.STATE.eq(DeliveryStateDbValue.PENDING.dbValue))
        .and(DELIVERY_QUEUE.USERNAME.eq(username))
        .and(DELIVERY_QUEUE.TARGET_ACTOR_URI.eq(followerActorUri))
        .execute()
}
