package net.matsudamper.mastodon.rss

import net.matsudamper.mastodon.rss.note.NoteCursor
import net.matsudamper.mastodon.rss.note.NoteStore
import net.matsudamper.mastodon.rss.note.StoredNote

/**
 * 投稿の記録の差し替え。オンメモリで持つ
 */
class FakeNoteStore : NoteStore {
    val added: MutableList<StoredNote> = mutableListOf()

    override fun add(note: StoredNote) {
        added += note
    }

    override fun find(publicId: String): StoredNote? = added.firstOrNull { it.publicId == publicId }

    override fun list(
        username: String,
        after: NoteCursor?,
        limit: Int,
    ): List<StoredNote> = added
        .filter { it.username == username }
        .sortedWith(compareByDescending<StoredNote> { it.publishedAt }.thenByDescending { it.publicId })
        .filter { note ->
            after == null ||
                note.publishedAt < after.publishedAt ||
                (note.publishedAt == after.publishedAt && note.publicId < after.publicId)
        }
        .take(limit)

    override fun count(username: String): Long = added.count { it.username == username }.toLong()
}
