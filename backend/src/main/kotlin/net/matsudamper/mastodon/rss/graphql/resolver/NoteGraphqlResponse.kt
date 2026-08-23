package net.matsudamper.mastodon.rss.graphql.resolver

import net.matsudamper.mastodon.rss.graphql.model.QlAccountNote
import net.matsudamper.mastodon.rss.graphql.model.QlAdminNote
import net.matsudamper.mastodon.rss.note.NoteUrls
import net.matsudamper.mastodon.rss.note.StoredNote

internal fun StoredNote.toGraphqlResponse(domain: String): QlAdminNote = QlAdminNote(
    url = NoteUrls(domain = domain, publicId = publicId).noteId,
    contentHtml = contentHtml,
    publishedAt = publishedAt.epochSecond,
)

internal fun StoredNote.toAccountNoteGraphqlResponse(domain: String): QlAccountNote = QlAccountNote(
    url = NoteUrls(domain = domain, publicId = publicId).noteId,
    contentHtml = contentHtml,
    publishedAt = publishedAt.epochSecond,
)
