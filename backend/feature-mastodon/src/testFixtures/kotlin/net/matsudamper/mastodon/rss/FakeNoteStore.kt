package net.matsudamper.mastodon.rss

import net.matsudamper.mastodon.rss.entity.PublicNoteId
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

    override fun find(publicId: PublicNoteId): StoredNote? = added.firstOrNull { it.publicId == publicId }

    override fun findByPublicIds(publicIds: Set<PublicNoteId>): Map<PublicNoteId, StoredNote> = added
        .filter { it.publicId in publicIds }
        .associateBy { it.publicId }

    override fun delete(publicId: PublicNoteId) {
        added.removeAll { it.publicId == publicId }
    }

    override fun deleteByUsername(username: String): Int {
        val before = added.size
        added.removeAll { it.username.equals(username, ignoreCase = true) }
        return before - added.size
    }

    override fun list(
        username: String,
        after: NotePosition?,
        limit: Int,
    ): List<StoredNote> = added
        .filter { it.username == username }
        .sortedWith(compareByDescending<StoredNote> { it.publishedAt }.thenByDescending { it.publicId.value })
        .filter { note ->
            after == null ||
                note.publishedAt < after.publishedAt ||
                (note.publishedAt == after.publishedAt && note.publicId.value < after.publicId.value)
        }
        .take(limit)

    override fun listPositions(
        username: String,
        after: NotePosition?,
        limit: Int,
    ): List<NotePosition> = list(username = username, after = after, limit = limit)
        .map { it.position }

    override fun count(username: String): Long = added.count { it.username == username }.toLong()

    override fun counts(usernames: Set<String>): Map<String, Long> =
        usernames.associateWith { count(it) }
}
