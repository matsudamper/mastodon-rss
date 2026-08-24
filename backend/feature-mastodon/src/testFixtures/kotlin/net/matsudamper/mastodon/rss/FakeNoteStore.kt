package net.matsudamper.mastodon.rss

import net.matsudamper.mastodon.rss.note.NotePosition
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

    override fun findByPublicIds(publicIds: Set<String>): Map<String, StoredNote> = added
        .filter { it.publicId in publicIds }
        .associateBy { it.publicId }

    override fun list(
        username: String,
        after: NotePosition?,
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

    override fun listPositions(
        username: String,
        after: NotePosition?,
        limit: Int,
    ): List<NotePosition> = list(username = username, after = after, limit = limit)
        .map { it.position }

    override fun count(username: String): Long = added.count { it.username == username }.toLong()
}
