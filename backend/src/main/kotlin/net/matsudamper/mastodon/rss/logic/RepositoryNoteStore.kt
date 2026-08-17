package net.matsudamper.mastodon.rss.logic

import net.matsudamper.mastodon.rss.note.NoteCursor
import net.matsudamper.mastodon.rss.note.NoteStore
import net.matsudamper.mastodon.rss.note.StoredNote
import net.matsudamper.mastodon.rss.repository.NewNote
import net.matsudamper.mastodon.rss.repository.Note
import net.matsudamper.mastodon.rss.repository.NoteRepository

/**
 * ActivityPub 側の [NoteStore] を DB に繋ぐ。
 * [RepositoryFollowerStore] と同じ理由で型を持ち替えるだけの層になる。
 */
class RepositoryNoteStore(
    private val notes: NoteRepository,
) : NoteStore {
    override fun add(note: StoredNote) {
        notes.add(
            NewNote(
                username = note.username,
                publicId = note.publicId,
                contentHtml = note.contentHtml,
                publishedAt = note.publishedAt,
            ),
        )
    }

    override fun find(publicId: String): StoredNote? = notes.find(publicId)?.toStored()

    override fun list(
        username: String,
        after: NoteCursor?,
        limit: Int,
    ): List<StoredNote> = notes
        .list(
            username = username,
            after = after?.let {
                net.matsudamper.mastodon.rss.repository.NoteCursor(
                    publishedAt = it.publishedAt,
                    publicId = it.publicId,
                )
            },
            limit = limit,
        )
        .map { it.toStored() }

    override fun count(username: String): Long = notes.count(username)

    private fun Note.toStored(): StoredNote = StoredNote(
        publicId = publicId,
        username = username,
        contentHtml = contentHtml,
        publishedAt = publishedAt,
    )
}
