package net.matsudamper.mastodon.rss.repository.sqlite

import net.matsudamper.mastodon.rss.repository.NewNote
import net.matsudamper.mastodon.rss.repository.Note
import net.matsudamper.mastodon.rss.repository.NoteCursor
import net.matsudamper.mastodon.rss.repository.NoteRepository
import net.matsudamper.mastodon.rss.repository.jooq.Tables.NOTES
import org.jooq.Condition
import org.jooq.Record
import org.jooq.impl.DSL

internal class SqliteNoteRepository(
    private val jooq: SqliteJooq,
) : NoteRepository {
    override fun add(note: NewNote) {
        jooq.transaction { dsl ->
            dsl
                .insertInto(NOTES)
                .set(NOTES.USERNAME, note.username)
                .set(NOTES.PUBLIC_ID, note.publicId)
                .set(NOTES.CONTENT_HTML, note.contentHtml)
                .set(NOTES.PUBLISHED_AT, StoredInstant.format(note.publishedAt))
                .execute()
        }
    }

    override fun find(publicId: String): Note? = jooq.transaction { dsl ->
        dsl
            .select(NOTES.PUBLIC_ID, NOTES.USERNAME, NOTES.CONTENT_HTML, NOTES.PUBLISHED_AT)
            .from(NOTES)
            .where(NOTES.PUBLIC_ID.eq(publicId))
            .fetchOne()
            ?.toNote()
    }

    override fun list(
        username: String,
        after: NoteCursor?,
        limit: Int,
    ): List<Note> = jooq.transaction { dsl ->
        dsl
            .select(NOTES.PUBLIC_ID, NOTES.USERNAME, NOTES.CONTENT_HTML, NOTES.PUBLISHED_AT)
            .from(NOTES)
            .where(NOTES.USERNAME.eq(username))
            .and(after?.let { olderThan(it) } ?: DSL.noCondition())
            // 新しい順。同じ時刻のものが並んだときの順を決めるために公開 id も見る。
            // 決めておかないと、ページをまたいで同じ投稿が 2 回出ることがある。
            // 採番した id ではなく公開 id を使うのは、この並びが位置を指す鍵になり、
            // 外に出す cursor にそのまま載るため
            .orderBy(NOTES.PUBLISHED_AT.desc(), NOTES.PUBLIC_ID.desc())
            .limit(limit)
            .fetch()
            .map { it.toNote() }
    }

    /**
     * 並び順で [cursor] より後ろにあるものを絞る条件。
     *
     * 並びが 2 列で決まるので、条件も 2 列で書く。時刻だけで比べると、
     * 同じ時刻の投稿がページの境目に来たときに落ちるか重複する。
     */
    private fun olderThan(cursor: NoteCursor): Condition {
        val publishedAt = StoredInstant.format(cursor.publishedAt)

        return NOTES.PUBLISHED_AT.lt(publishedAt)
            .or(NOTES.PUBLISHED_AT.eq(publishedAt).and(NOTES.PUBLIC_ID.lt(cursor.publicId)))
    }

    override fun count(username: String): Long = jooq.transaction { dsl ->
        dsl
            .selectCount()
            .from(NOTES)
            .where(NOTES.USERNAME.eq(username))
            .fetchOne(0, Int::class.java)
            ?.toLong() ?: 0L
    }

    private fun Record.toNote(): Note = Note(
        publicId = get(NOTES.PUBLIC_ID),
        username = get(NOTES.USERNAME),
        contentHtml = get(NOTES.CONTENT_HTML),
        publishedAt = StoredInstant.parse(get(NOTES.PUBLISHED_AT)),
    )
}
