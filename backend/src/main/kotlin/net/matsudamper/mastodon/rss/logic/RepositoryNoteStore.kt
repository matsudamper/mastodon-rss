package net.matsudamper.mastodon.rss.logic

import net.matsudamper.mastodon.rss.entity.PublicNoteId as MastodonPublicNoteId
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
                publicId = PublicNoteId(note.publicId.value),
                contentHtml = note.contentHtml,
                publishedAt = note.publishedAt,
            ),
        )
    }

    override fun find(publicId: MastodonPublicNoteId): StoredNote? = notes.find(PublicNoteId(publicId.value))?.toStored()

    override fun findByPublicIds(publicIds: Set<MastodonPublicNoteId>): Map<MastodonPublicNoteId, StoredNote> = notes
        .findByPublicIds(publicIds.map { PublicNoteId(it.value) }.toSet())
        .map { (publicId, note) -> MastodonPublicNoteId(publicId.value) to note.toStored() }
        .toMap()

    override fun delete(publicId: MastodonPublicNoteId) {
        notes.delete(PublicNoteId(publicId.value))
    }

    override fun deleteByUsername(username: String): Int = notes.deleteByUsername(username)

    override fun list(
        username: String,
        after: NotePosition?,
        limit: Int,
    ): List<StoredNote> = notes
        .list(
            username = username,
            after = after?.toRepository(),
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
            after = after?.toRepository(),
            limit = limit,
        )
        .map {
            NotePosition(
                publishedAt = it.publishedAt,
                publicId = MastodonPublicNoteId(it.publicId.value),
            )
        }

    override fun count(username: String): Long = notes.count(username)

    override fun counts(usernames: Set<String>): Map<String, Long> = notes.counts(usernames)

    private fun NotePosition.toRepository(): net.matsudamper.mastodon.rss.repository.NotePosition =
        net.matsudamper.mastodon.rss.repository.NotePosition(
            publishedAt = publishedAt,
            publicId = PublicNoteId(publicId.value),
        )

    private fun Note.toStored(): StoredNote = StoredNote(
        publicId = MastodonPublicNoteId(publicId.value),
        username = username,
        contentHtml = contentHtml,
        publishedAt = publishedAt,
    )
}
