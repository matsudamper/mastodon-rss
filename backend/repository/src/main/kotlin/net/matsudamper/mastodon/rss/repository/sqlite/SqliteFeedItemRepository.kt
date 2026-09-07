package net.matsudamper.mastodon.rss.repository.sqlite

import java.time.Instant
import net.matsudamper.mastodon.rss.repository.FeedItem
import net.matsudamper.mastodon.rss.repository.FeedItemRepository
import net.matsudamper.mastodon.rss.repository.NewFeedItem
import net.matsudamper.mastodon.rss.repository.NewNote
import net.matsudamper.mastodon.rss.repository.entity.FeedId
import net.matsudamper.mastodon.rss.repository.entity.FeedItemId
import net.matsudamper.mastodon.rss.repository.jooq.Tables.FEED_ITEMS
import net.matsudamper.mastodon.rss.repository.jooq.Tables.NOTES
import net.matsudamper.mastodon.rss.repository.jooq.tables.records.FeedItemsRecord
import net.matsudamper.mastodon.rss.repository.sqlite.db.FeedItemStateDbValue
import net.matsudamper.mastodon.rss.shared.PublicNoteId

internal class SqliteFeedItemRepository(
    private val jooq: SqliteJooq,
) : FeedItemRepository {
    override fun findExistingKeys(
        feedId: FeedId,
        keys: Collection<String>,
    ): Set<String> {
        if (keys.isEmpty()) return emptySet()

        return jooq.withConnection { dsl ->
            dsl
                .select(FEED_ITEMS.ITEM_KEY)
                .from(FEED_ITEMS)
                .where(FEED_ITEMS.FEED_ID.eq(feedId.value))
                .and(FEED_ITEMS.ITEM_KEY.`in`(keys))
                .fetchSet(FEED_ITEMS.ITEM_KEY)
        }
    }

    override fun add(item: NewFeedItem): FeedItem? = jooq.transaction { dsl ->
        val exists =
            dsl
                .select(FEED_ITEMS.ID)
                .from(FEED_ITEMS)
                .where(FEED_ITEMS.FEED_ID.eq(item.feedId.value))
                .and(FEED_ITEMS.ITEM_KEY.eq(item.itemKey))
                .fetchOne()
        if (exists != null) return@transaction null

        val id = dsl
            .insertInto(FEED_ITEMS)
            .set(FEED_ITEMS.FEED_ID, item.feedId.value)
            .set(FEED_ITEMS.ITEM_KEY, item.itemKey)
            .set(FEED_ITEMS.TITLE, item.title)
            .set(FEED_ITEMS.LINK, item.link)
            .set(FEED_ITEMS.CONTENT_HTML, item.contentHtml)
            .set(FEED_ITEMS.PUBLISHED_AT, item.publishedAt?.let(StoredInstant::format))
            .set(FEED_ITEMS.IMPORTED_AT, StoredInstant.format(item.importedAt))
            .set(FEED_ITEMS.STATE, FeedItemStateDbValue.of(item.state).dbValue)
            .set(FEED_ITEMS.POSTED_AT, null as String?)
            .set(FEED_ITEMS.NOTE_ID, null as String?)
            .returning(FEED_ITEMS.ID)
            .fetchOne()
            ?.id
            ?: error("記事の追加に失敗した")

        FeedItem(
            id = FeedItemId(id),
            feedId = item.feedId,
            itemKey = item.itemKey,
            title = item.title,
            link = item.link,
            contentHtml = item.contentHtml,
            publishedAt = item.publishedAt,
            importedAt = item.importedAt,
            state = item.state,
            postedAt = null,
            noteId = null,
        )
    }

    override fun findPending(limit: Int): List<FeedItem> = loadPending(feedId = null, limit = limit)

    override fun findPending(
        feedId: FeedId,
        limit: Int,
    ): List<FeedItem> = loadPending(feedId = feedId, limit = limit)

    override fun linkNote(
        feedId: FeedItemId,
        noteId: PublicNoteId,
    ): PublicNoteId = jooq.transaction { dsl ->
        dsl
            .update(FEED_ITEMS)
            .set(FEED_ITEMS.NOTE_ID, noteId.value)
            .where(FEED_ITEMS.ID.eq(feedId.value))
            .and(FEED_ITEMS.NOTE_ID.isNull())
            .execute()

        dsl
            .select(FEED_ITEMS.NOTE_ID)
            .from(FEED_ITEMS)
            .where(FEED_ITEMS.ID.eq(feedId.value))
            .fetchOne(FEED_ITEMS.NOTE_ID)
            ?.let(::PublicNoteId)
            ?: error("記事に投稿を紐付けられなかった")
    }

    override fun linkNote(
        feedId: FeedItemId,
        note: NewNote,
    ): PublicNoteId = jooq.transaction { dsl ->
        dsl
            .insertInto(NOTES)
            .set(NOTES.USERNAME, note.username)
            .set(NOTES.PUBLIC_ID, note.publicId.value)
            .set(NOTES.CONTENT_HTML, note.contentHtml)
            .set(NOTES.PUBLISHED_AT, StoredInstant.format(note.publishedAt))
            .execute()

        val linked = dsl
            .update(FEED_ITEMS)
            .set(FEED_ITEMS.NOTE_ID, note.publicId.value)
            .where(FEED_ITEMS.ID.eq(feedId.value))
            .and(FEED_ITEMS.NOTE_ID.isNull())
            .execute()

        if (linked == 1) {
            return@transaction note.publicId
        }

        val existing = dsl
            .select(FEED_ITEMS.NOTE_ID)
            .from(FEED_ITEMS)
            .where(FEED_ITEMS.ID.eq(feedId.value))
            .fetchOne(FEED_ITEMS.NOTE_ID)
            ?: error("記事に投稿を紐付けられなかった")

        // 別の投稿が先に紐付いていた。新しく作った投稿は外から見える前に同じ
        // トランザクション内で捨てて、先に確定した id を使う。
        dsl
            .deleteFrom(NOTES)
            .where(NOTES.PUBLIC_ID.eq(note.publicId.value))
            .execute()

        PublicNoteId(existing)
    }

    override fun markPosted(
        id: FeedItemId,
        postedAt: Instant,
        noteId: PublicNoteId,
    ) {
        jooq.transaction { dsl ->
            dsl
                .update(FEED_ITEMS)
                .set(FEED_ITEMS.STATE, FeedItemStateDbValue.POSTED.dbValue)
                .set(FEED_ITEMS.POSTED_AT, StoredInstant.format(postedAt))
                .set(FEED_ITEMS.NOTE_ID, noteId.value)
                .where(FEED_ITEMS.ID.eq(id.value))
                .execute()
        }
    }

    override fun markSkipped(id: FeedItemId) {
        jooq.transaction { dsl ->
            dsl
                .update(FEED_ITEMS)
                .set(FEED_ITEMS.STATE, FeedItemStateDbValue.SKIPPED.dbValue)
                .where(FEED_ITEMS.ID.eq(id.value))
                .execute()
        }
    }

    override fun findByNoteIds(noteIds: Collection<PublicNoteId>): Map<PublicNoteId, FeedItem> {
        if (noteIds.isEmpty()) return emptyMap()

        return jooq.withConnection { dsl ->
            dsl
                .selectFrom(FEED_ITEMS)
                .where(FEED_ITEMS.NOTE_ID.`in`(noteIds.map { it.value }))
                .fetch()
                .map { it.toFeedItem() }
                .associateBy { checkNotNull(it.noteId) }
        }
    }

    override fun find(id: FeedItemId): FeedItem? = jooq.withConnection { dsl ->
        dsl
            .selectFrom(FEED_ITEMS)
            .where(FEED_ITEMS.ID.eq(id.value))
            .fetchOne()
            ?.toFeedItem()
    }

    override fun delete(
        feedId: FeedId,
        ids: Collection<FeedItemId>,
    ): Boolean {
        val targets = ids.toSet()
        if (targets.isEmpty()) return true

        return try {
            jooq.transaction { dsl ->
                val deleted = dsl
                    .deleteFrom(FEED_ITEMS)
                    .where(FEED_ITEMS.ID.`in`(targets.map { it.value }))
                    .and(FEED_ITEMS.FEED_ID.eq(feedId.value))
                    .execute()
                // このフィードに無い id が混ざっている。消した分を巻き戻す
                if (deleted != targets.size) throw NotAllDeleted()
                true
            }
        } catch (_: NotAllDeleted) {
            false
        }
    }

    private class NotAllDeleted : RuntimeException()

    override fun countByFeed(feedId: FeedId): Long = jooq.withConnection { dsl ->
        dsl
            .selectCount()
            .from(FEED_ITEMS)
            .where(FEED_ITEMS.FEED_ID.eq(feedId.value))
            .fetchOne(0, Long::class.java)
            ?: 0L
    }

    private fun loadPending(
        feedId: FeedId?,
        limit: Int,
    ): List<FeedItem> {
        if (limit <= 0) return emptyList()

        return jooq.withConnection { dsl ->
            val condition = FEED_ITEMS.STATE.eq(FeedItemStateDbValue.PENDING.dbValue).let { pending ->
                if (feedId == null) pending else pending.and(FEED_ITEMS.FEED_ID.eq(feedId.value))
            }

            dsl
                .selectFrom(FEED_ITEMS)
                .where(condition)
                .fetch()
                .map { it.toFeedItem() }
                .sortedWith(pendingOrder)
                .take(limit)
        }
    }

    private fun FeedItemsRecord.toFeedItem(): FeedItem = FeedItem(
        id = FeedItemId(id!!),
        feedId = FeedId(feedId!!),
        itemKey = itemKey!!,
        title = title,
        link = link,
        contentHtml = contentHtml,
        publishedAt = publishedAt?.let(StoredInstant::parse),
        importedAt = StoredInstant.parse(importedAt!!),
        state = FeedItemStateDbValue.parse(state!!).toFeedItemState(),
        postedAt = postedAt?.let(StoredInstant::parse),
        noteId = noteId?.let(::PublicNoteId),
    )
}

private val pendingOrder: Comparator<FeedItem> =
    compareBy<FeedItem> { it.publishedAt == null }
        .thenBy { it.publishedAt }
        .thenBy { it.id.value }
