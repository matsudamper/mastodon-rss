package net.matsudamper.mastodon.rss.logic

import net.matsudamper.mastodon.rss.note.NotePosition
import net.matsudamper.mastodon.rss.note.NoteStore
import net.matsudamper.mastodon.rss.note.StoredNote
import net.matsudamper.mastodon.rss.repository.NewNote
import net.matsudamper.mastodon.rss.repository.Note
import net.matsudamper.mastodon.rss.repository.NoteRepository
import net.matsudamper.mastodon.rss.shared.PublicNoteId

/**
 * ActivityPub 側の [NoteStore] を DB に繋ぐ。
 * [RepositoryFollowerStore] と同じく型を持ち替えるだけの層になる。
 */
class RepositoryNoteStore(
    private val notes: NoteRepository,
) : NoteStore {
    override fun add(note: StoredNote) {
        notes.add(
            NewNote(
                username = note.username,
                publicId = PublicNoteId(note.publicId),
                contentHtml = note.contentHtml,
                publishedAt = note.publishedAt,
            ),
        )
    }

    override fun find(publicId: String): StoredNote? = notes.find(PublicNoteId(publicId))?.toStored()

    override fun findByPublicIds(publicIds: Set<String>): Map<String, StoredNote> = notes
        .findByPublicIds(publicIds.map { PublicNoteId(it) }.toSet())
        .entries
        .associate { (publicId, note) -> publicId.value to note.toStored() }

    override fun delete(publicId: String) {
        notes.delete(PublicNoteId(publicId))
    }

    override fun list(
        username: String,
        after: NotePosition?,
        limit: Int,
    ): List<StoredNote> = notes
        .list(
            username = username,
            after = after?.let {
                net.matsudamper.mastodon.rss.repository.NotePosition(
                    publishedAt = it.publishedAt,
                    publicId = PublicNoteId(it.publicId),
                )
            },
            limit = limit,
        )
        .map { it.toStored() }

    override fun listPositions(
        username: String,
        after: NotePosition?,
        limit: Int,
    ): List<NotePosition> = notes
        .listPositions(
            username = username,
            after = after?.let {
                net.matsudamper.mastodon.rss.repository.NotePosition(
                    publishedAt = it.publishedAt,
                    publicId = PublicNoteId(it.publicId),
                )
            },
            limit = limit,
        )
        .map {
            NotePosition(
                publishedAt = it.publishedAt,
                publicId = it.publicId.value,
            )
        }

    override fun count(username: String): Long = notes.count(username)

    private fun Note.toStored(): StoredNote = StoredNote(
        publicId = publicId.value,
        username = username,
        contentHtml = contentHtml,
        publishedAt = publishedAt,
    )
}
