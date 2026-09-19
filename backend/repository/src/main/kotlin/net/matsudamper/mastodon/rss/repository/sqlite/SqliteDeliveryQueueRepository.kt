package net.matsudamper.mastodon.rss.repository.sqlite

import java.time.Instant
import net.matsudamper.mastodon.rss.repository.ActorUpdatePost
import net.matsudamper.mastodon.rss.repository.ClaimedDelivery
import net.matsudamper.mastodon.rss.repository.DeliveredOutcome
import net.matsudamper.mastodon.rss.repository.DeliveryQueueCounts
import net.matsudamper.mastodon.rss.repository.DeliveryQueuePosition
import net.matsudamper.mastodon.rss.repository.DeliveryQueueRepository
import net.matsudamper.mastodon.rss.repository.EnqueueNoteResult
import net.matsudamper.mastodon.rss.repository.FailedDelivery
import net.matsudamper.mastodon.rss.repository.NoteDeletionPost
import net.matsudamper.mastodon.rss.repository.NotePost
import net.matsudamper.mastodon.rss.repository.RecordedNotePost
import net.matsudamper.mastodon.rss.repository.RetryingDelivery
import net.matsudamper.mastodon.rss.repository.UnsentDelivery
import net.matsudamper.mastodon.rss.repository.entity.DeliveryId
import net.matsudamper.mastodon.rss.repository.entity.FeedItemId
import net.matsudamper.mastodon.rss.repository.jooq.Tables.DELIVERY_QUEUE
import net.matsudamper.mastodon.rss.repository.jooq.Tables.FEED_ITEMS
import net.matsudamper.mastodon.rss.repository.jooq.Tables.NOTES
import net.matsudamper.mastodon.rss.repository.sqlite.db.DeliveryKindDbValue
import net.matsudamper.mastodon.rss.repository.sqlite.db.DeliveryStateDbValue
import net.matsudamper.mastodon.rss.repository.sqlite.db.FeedItemStateDbValue
import net.matsudamper.mastodon.rss.shared.PublicNoteId
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL

internal class SqliteDeliveryQueueRepository(
    private val jooq: SqliteJooq,
) : DeliveryQueueRepository {
    override fun enqueueNote(post: NotePost): EnqueueNoteResult =
        try {
            jooq.transaction { dsl ->
                // 記事の note_id は notes.public_id を参照しているので、投稿を先に入れる。
                // 記事が pending でなければ、入れた投稿ごと巻き戻す
                dsl
                    .insertInto(NOTES)
                    .set(NOTES.USERNAME, post.note.username)
                    .set(NOTES.PUBLIC_ID, post.note.publicId.value)
                    .set(NOTES.CONTENT_HTML, post.note.contentHtml)
                    .set(NOTES.PUBLISHED_AT, StoredInstant.format(post.note.publishedAt))
                    .execute()

                val feedItemId = post.feedItemId
                if (feedItemId != null) {
                    markPosted(
                        dsl = dsl,
                        feedItemId = feedItemId,
                        notePublicId = post.note.publicId,
                        postedAt = post.enqueuedAt,
                    )
                }

                insertDeliveries(
                    dsl = dsl,
                    username = post.note.username,
                    notePublicId = post.note.publicId,
                    body = post.body,
                    inboxes = post.inboxes,
                    enqueuedAt = post.enqueuedAt,
                )

                EnqueueNoteResult.Queued(deliveries = post.inboxes.size)
            }
        } catch (_: FeedItemNotPending) {
            EnqueueNoteResult.FeedItemNotPending
        }

    override fun requeueNote(post: RecordedNotePost): EnqueueNoteResult =
        try {
            jooq.transaction { dsl ->
                markPosted(
                    dsl = dsl,
                    feedItemId = post.feedItemId,
                    notePublicId = post.publicId,
                    postedAt = post.enqueuedAt,
                )

                insertDeliveries(
                    dsl = dsl,
                    username = post.username,
                    notePublicId = post.publicId,
                    body = post.body,
                    inboxes = post.inboxes,
                    enqueuedAt = post.enqueuedAt,
                )

                EnqueueNoteResult.Queued(deliveries = post.inboxes.size)
            }
        } catch (_: FeedItemNotPending) {
            EnqueueNoteResult.FeedItemNotPending
        }

    override fun enqueueNoteDeletion(post: NoteDeletionPost): Int = jooq.transaction { dsl ->
        // 未配信の Create は外部キーで一緒に消える。残すと、消した投稿が後から届く
        dsl
            .deleteFrom(NOTES)
            .where(NOTES.PUBLIC_ID.eq(post.publicId.value))
            .execute()

        post.inboxes.forEach { inbox ->
            DeliveryQueueRows.insertPending(
                dsl = dsl,
                kind = DeliveryKindDbValue.DELETE_NOTE,
                username = post.username,
                inbox = inbox,
                body = post.body,
                enqueuedAt = post.enqueuedAt,
                // いま消した投稿に紐付けると、この行も一緒に消える
                notePublicId = null,
                targetActorUri = null,
            )
        }

        post.inboxes.size
    }

    override fun enqueueActorUpdate(post: ActorUpdatePost): Int = jooq.transaction { dsl ->
        // 送り残した古い更新を残すと、それが後から届いて相手の表示が 1 つ前に戻る。
        // 送っている最中の行も消す。残すと、送れなかったときに送り直し待ちに戻って、
        // 新しい更新が届いた後から古い内容が届く
        dsl
            .deleteFrom(DELIVERY_QUEUE)
            .where(DELIVERY_QUEUE.KIND.eq(DeliveryKindDbValue.UPDATE_ACTOR.dbValue))
            .and(
                DELIVERY_QUEUE.STATE.`in`(
                    DeliveryStateDbValue.PENDING.dbValue,
                    DeliveryStateDbValue.DELIVERING.dbValue,
                ),
            )
            .and(DELIVERY_QUEUE.USERNAME.eq(post.username))
            .execute()

        post.inboxes.forEach { inbox ->
            DeliveryQueueRows.insertPending(
                dsl = dsl,
                kind = DeliveryKindDbValue.UPDATE_ACTOR,
                username = post.username,
                inbox = inbox,
                body = post.body,
                enqueuedAt = post.enqueuedAt,
                notePublicId = null,
                targetActorUri = null,
            )
        }

        post.inboxes.size
    }

    /**
     * 記事を投稿済みにする。`pending` でなければ [FeedItemNotPending] でトランザクションごと巻き戻す
     */
    private fun markPosted(
        dsl: DSLContext,
        feedItemId: FeedItemId,
        notePublicId: PublicNoteId,
        postedAt: Instant,
    ) {
        // pending のときだけ更新できる。0 件なら別の経路が先に投稿している
        val updated = dsl
            .update(FEED_ITEMS)
            .set(FEED_ITEMS.STATE, FeedItemStateDbValue.POSTED.dbValue)
            .set(FEED_ITEMS.POSTED_AT, StoredInstant.format(postedAt))
            .set(FEED_ITEMS.NOTE_ID, notePublicId.value)
            .where(FEED_ITEMS.ID.eq(feedItemId.value))
            .and(FEED_ITEMS.STATE.eq(FeedItemStateDbValue.PENDING.dbValue))
            .execute()
        if (updated != 1) throw FeedItemNotPending()
    }

    private fun insertDeliveries(
        dsl: DSLContext,
        username: String,
        notePublicId: PublicNoteId,
        body: String,
        inboxes: List<String>,
        enqueuedAt: Instant,
    ) {
        inboxes.forEach { inbox ->
            DeliveryQueueRows.insertPending(
                dsl = dsl,
                kind = DeliveryKindDbValue.CREATE_NOTE,
                username = username,
                inbox = inbox,
                body = body,
                enqueuedAt = enqueuedAt,
                notePublicId = notePublicId.value,
                targetActorUri = null,
            )
        }
    }

    private class FeedItemNotPending : RuntimeException()

    override fun claim(
        now: Instant,
        limit: Int,
    ): List<ClaimedDelivery> {
        if (limit <= 0) return emptyList()

        val dueBy = StoredInstant.format(now)

        return jooq.transaction { dsl ->
            // 送る時刻が最も古い行を持つホストから順に、ホストごとに 1 件だけ選ぶ。
            // 行を古い順に取ると、溜まった 1 ホストで 1 回分が埋まって他のホスト宛が待つ
            val hosts = dsl
                .select(DELIVERY_QUEUE.INBOX_HOST, DSL.min(DELIVERY_QUEUE.NEXT_ATTEMPT_AT))
                .from(DELIVERY_QUEUE)
                .where(DELIVERY_QUEUE.STATE.eq(DeliveryStateDbValue.PENDING.dbValue))
                .and(DELIVERY_QUEUE.NEXT_ATTEMPT_AT.le(dueBy))
                .groupBy(DELIVERY_QUEUE.INBOX_HOST)
                .orderBy(DSL.min(DELIVERY_QUEUE.NEXT_ATTEMPT_AT).asc(), DELIVERY_QUEUE.INBOX_HOST.asc())
                .limit(limit)
                .fetch(DELIVERY_QUEUE.INBOX_HOST)

            val candidates = hosts.mapNotNull { host ->
                dsl
                    .select(DELIVERY_QUEUE.ID)
                    .from(DELIVERY_QUEUE)
                    .where(DELIVERY_QUEUE.STATE.eq(DeliveryStateDbValue.PENDING.dbValue))
                    .and(DELIVERY_QUEUE.NEXT_ATTEMPT_AT.le(dueBy))
                    .and(DELIVERY_QUEUE.INBOX_HOST.eq(host))
                    .orderBy(DELIVERY_QUEUE.NEXT_ATTEMPT_AT.asc(), DELIVERY_QUEUE.ID.asc())
                    .limit(1)
                    .fetchOne(DELIVERY_QUEUE.ID)
            }

            // 条件に state を入れて、実際に更新できた行だけを返す
            val claimedIds = candidates.filter { id ->
                dsl
                    .update(DELIVERY_QUEUE)
                    .set(DELIVERY_QUEUE.STATE, DeliveryStateDbValue.DELIVERING.dbValue)
                    .set(DELIVERY_QUEUE.ATTEMPTS, DELIVERY_QUEUE.ATTEMPTS.plus(1))
                    .where(DELIVERY_QUEUE.ID.eq(id))
                    .and(DELIVERY_QUEUE.STATE.eq(DeliveryStateDbValue.PENDING.dbValue))
                    .execute() == 1
            }
            if (claimedIds.isEmpty()) return@transaction emptyList()

            dsl
                .selectFrom(DELIVERY_QUEUE)
                .where(DELIVERY_QUEUE.ID.`in`(claimedIds))
                .orderBy(DELIVERY_QUEUE.NEXT_ATTEMPT_AT.asc(), DELIVERY_QUEUE.ID.asc())
                .fetch()
                .map { it.toClaimed() }
        }
    }

    override fun exists(id: DeliveryId): Boolean = jooq.withConnection { dsl ->
        dsl.fetchExists(DSL.selectOne().from(DELIVERY_QUEUE).where(DELIVERY_QUEUE.ID.eq(id.value)))
    }

    /**
     * 行を消すのと、送れたことで確定する記録を 1 トランザクションで書く
     */
    override fun markDelivered(
        id: DeliveryId,
        deliveredAt: Instant,
    ): DeliveredOutcome = jooq.transaction { dsl ->
        // 送っている間に投稿やアカウントが消されると、行ごと消える
        val row = dsl
            .selectFrom(DELIVERY_QUEUE)
            .where(DELIVERY_QUEUE.ID.eq(id.value))
            .fetchOne()
            ?: return@transaction DeliveredOutcome.None

        dsl
            .deleteFrom(DELIVERY_QUEUE)
            .where(DELIVERY_QUEUE.ID.eq(id.value))
            .execute()

        when (DeliveryKindDbValue.parse(row.get(DELIVERY_QUEUE.KIND))) {
            DeliveryKindDbValue.CREATE_NOTE,
            DeliveryKindDbValue.DELETE_NOTE,
            DeliveryKindDbValue.UPDATE_ACTOR,
            DeliveryKindDbValue.DELETE_ACTOR,
            -> DeliveredOutcome.None

            DeliveryKindDbValue.ACCEPT_FOLLOW -> {
                val username = row.get(DELIVERY_QUEUE.USERNAME)
                val followerActorUri = checkNotNull(row.get(DELIVERY_QUEUE.TARGET_ACTOR_URI)) {
                    "accept_follow の行に相手のアクターが無い"
                }

                val firstAccept = FollowerRows.markAccepted(
                    dsl = dsl,
                    username = username,
                    followerActorUri = followerActorUri,
                    acceptedAt = deliveredAt,
                )

                if (firstAccept) {
                    DeliveredOutcome.FollowAccepted(
                        username = username,
                        followerActorUri = followerActorUri,
                        inbox = row.get(DELIVERY_QUEUE.INBOX),
                    )
                } else {
                    DeliveredOutcome.None
                }
            }
        }
    }

    override fun scheduleRetry(
        id: DeliveryId,
        nextAttemptAt: Instant,
        error: String,
    ) {
        jooq.transaction { dsl ->
            dsl
                .update(DELIVERY_QUEUE)
                .set(DELIVERY_QUEUE.STATE, DeliveryStateDbValue.PENDING.dbValue)
                .set(DELIVERY_QUEUE.NEXT_ATTEMPT_AT, StoredInstant.format(nextAttemptAt))
                .set(DELIVERY_QUEUE.LAST_ERROR, error)
                .where(DELIVERY_QUEUE.ID.eq(id.value))
                .execute()
        }
    }

    override fun giveUp(
        id: DeliveryId,
        error: String,
    ) {
        jooq.transaction { dsl ->
            dsl
                .update(DELIVERY_QUEUE)
                .set(DELIVERY_QUEUE.STATE, DeliveryStateDbValue.FAILED.dbValue)
                .set(DELIVERY_QUEUE.NEXT_ATTEMPT_AT, null as String?)
                .set(DELIVERY_QUEUE.BODY, null as String?)
                .set(DELIVERY_QUEUE.LAST_ERROR, error)
                .where(DELIVERY_QUEUE.ID.eq(id.value))
                .execute()
        }
    }

    override fun recoverDelivering(): Int = jooq.transaction { dsl ->
        dsl
            .update(DELIVERY_QUEUE)
            .set(DELIVERY_QUEUE.STATE, DeliveryStateDbValue.PENDING.dbValue)
            .where(DELIVERY_QUEUE.STATE.eq(DeliveryStateDbValue.DELIVERING.dbValue))
            .execute()
    }

    override fun hasUnsent(): Boolean = jooq.withConnection { dsl ->
        dsl.fetchExists(
            DSL
                .selectOne()
                .from(DELIVERY_QUEUE)
                .where(DELIVERY_QUEUE.STATE.ne(DeliveryStateDbValue.FAILED.dbValue)),
        )
    }

    override fun counts(username: String): DeliveryQueueCounts = jooq.withConnection { dsl ->
        val byState = dsl
            .select(DELIVERY_QUEUE.STATE, DSL.count())
            .from(DELIVERY_QUEUE)
            .where(DELIVERY_QUEUE.USERNAME.eq(username))
            .groupBy(DELIVERY_QUEUE.STATE)
            .fetch()
            .associate { it.value1() to it.value2().toLong() }

        DeliveryQueueCounts(
            waiting = (byState[DeliveryStateDbValue.PENDING.dbValue] ?: 0L) +
                (byState[DeliveryStateDbValue.DELIVERING.dbValue] ?: 0L),
            failed = byState[DeliveryStateDbValue.FAILED.dbValue] ?: 0L,
        )
    }

    override fun listUnsent(
        after: DeliveryQueuePosition?,
        limit: Int,
    ): List<UnsentDelivery> {
        if (limit <= 0) return emptyList()

        return jooq.withConnection { dsl ->
            dsl
                .selectFrom(DELIVERY_QUEUE)
                .where(DELIVERY_QUEUE.STATE.ne(DeliveryStateDbValue.FAILED.dbValue))
                .and(after?.let { laterThan(it) } ?: DSL.noCondition())
                .orderBy(DELIVERY_QUEUE.NEXT_ATTEMPT_AT.asc(), DELIVERY_QUEUE.ID.asc())
                .limit(limit)
                .fetch()
                .map { record ->
                    UnsentDelivery(
                        id = DeliveryId(record.get(DELIVERY_QUEUE.ID)),
                        kind = DeliveryKindDbValue.parse(record.get(DELIVERY_QUEUE.KIND)).toDeliveryKind(),
                        username = record.get(DELIVERY_QUEUE.USERNAME),
                        inbox = record.get(DELIVERY_QUEUE.INBOX),
                        attempts = record.get(DELIVERY_QUEUE.ATTEMPTS).toInt(),
                        // 諦めた行を除いてあるので、次に送る時刻は必ずある
                        nextAttemptAt = StoredInstant.parse(record.get(DELIVERY_QUEUE.NEXT_ATTEMPT_AT)),
                        sending = record.get(DELIVERY_QUEUE.STATE) == DeliveryStateDbValue.DELIVERING.dbValue,
                        lastError = record.get(DELIVERY_QUEUE.LAST_ERROR),
                    )
                }
        }
    }

    override fun listRetrying(
        username: String,
        after: DeliveryQueuePosition?,
        limit: Int,
    ): List<RetryingDelivery> {
        if (limit <= 0) return emptyList()

        return jooq.withConnection { dsl ->
            dsl
                .selectFrom(DELIVERY_QUEUE)
                .where(DELIVERY_QUEUE.USERNAME.eq(username))
                .and(DELIVERY_QUEUE.STATE.eq(DeliveryStateDbValue.PENDING.dbValue))
                .and(DELIVERY_QUEUE.ATTEMPTS.gt(0L))
                .and(after?.let { laterThan(it) } ?: DSL.noCondition())
                .orderBy(DELIVERY_QUEUE.NEXT_ATTEMPT_AT.asc(), DELIVERY_QUEUE.ID.asc())
                .limit(limit)
                .fetch()
                .map { record ->
                    RetryingDelivery(
                        id = DeliveryId(record.get(DELIVERY_QUEUE.ID)),
                        kind = DeliveryKindDbValue.parse(record.get(DELIVERY_QUEUE.KIND)).toDeliveryKind(),
                        inbox = record.get(DELIVERY_QUEUE.INBOX),
                        attempts = record.get(DELIVERY_QUEUE.ATTEMPTS).toInt(),
                        nextAttemptAt = StoredInstant.parse(record.get(DELIVERY_QUEUE.NEXT_ATTEMPT_AT)),
                        lastError = record.get(DELIVERY_QUEUE.LAST_ERROR),
                    )
                }
        }
    }

    /**
     * 並び順で [cursor] より後ろにあるものを絞る条件。
     *
     * 時刻だけで比べると、同じ時刻の行がページの境目に来たときに落ちるか重複する
     */
    private fun laterThan(cursor: DeliveryQueuePosition): Condition {
        val nextAttemptAt = StoredInstant.format(cursor.nextAttemptAt)

        return DELIVERY_QUEUE.NEXT_ATTEMPT_AT.gt(nextAttemptAt)
            .or(DELIVERY_QUEUE.NEXT_ATTEMPT_AT.eq(nextAttemptAt).and(DELIVERY_QUEUE.ID.gt(cursor.id.value)))
    }

    override fun listFailed(
        username: String,
        afterId: DeliveryId?,
        limit: Int,
    ): List<FailedDelivery> {
        if (limit <= 0) return emptyList()

        return jooq.withConnection { dsl ->
            dsl
                .selectFrom(DELIVERY_QUEUE)
                .where(DELIVERY_QUEUE.USERNAME.eq(username))
                .and(DELIVERY_QUEUE.STATE.eq(DeliveryStateDbValue.FAILED.dbValue))
                .and(afterId?.let { DELIVERY_QUEUE.ID.lt(it.value) } ?: DSL.noCondition())
                .orderBy(DELIVERY_QUEUE.ID.desc())
                .limit(limit)
                .fetch()
                .map { record ->
                    FailedDelivery(
                        id = DeliveryId(record.get(DELIVERY_QUEUE.ID)),
                        kind = DeliveryKindDbValue.parse(record.get(DELIVERY_QUEUE.KIND)).toDeliveryKind(),
                        inbox = record.get(DELIVERY_QUEUE.INBOX),
                        attempts = record.get(DELIVERY_QUEUE.ATTEMPTS).toInt(),
                        lastError = record.get(DELIVERY_QUEUE.LAST_ERROR),
                    )
                }
        }
    }

    override fun deleteByUsername(username: String): Int = jooq.transaction { dsl ->
        dsl
            .deleteFrom(DELIVERY_QUEUE)
            .where(DELIVERY_QUEUE.USERNAME.eq(username))
            .execute()
    }

    private fun Record.toClaimed(): ClaimedDelivery = ClaimedDelivery(
        id = DeliveryId(get(DELIVERY_QUEUE.ID)),
        kind = DeliveryKindDbValue.parse(get(DELIVERY_QUEUE.KIND)).toDeliveryKind(),
        username = get(DELIVERY_QUEUE.USERNAME),
        inbox = get(DELIVERY_QUEUE.INBOX),
        // delivering にした行は body を消していないので必ずある
        body = checkNotNull(get(DELIVERY_QUEUE.BODY)) { "delivering の行に body が無い" },
        attempts = get(DELIVERY_QUEUE.ATTEMPTS).toInt(),
        enqueuedAt = StoredInstant.parse(get(DELIVERY_QUEUE.ENQUEUED_AT)),
    )
}
