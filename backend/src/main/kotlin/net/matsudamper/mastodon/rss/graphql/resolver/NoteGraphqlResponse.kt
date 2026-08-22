package net.[REDACTED].mastodon.rss.graphql.resolver

import net.[REDACTED].mastodon.rss.graphql.model.QlAdminNote
import net.[REDACTED].mastodon.rss.graphql.model.QlNote
import net.[REDACTED].mastodon.rss.note.NoteUrls
import net.[REDACTED].mastodon.rss.note.StoredNote

internal fun StoredNote.toGraphqlResponse(domain: String): QlAdminNote = QlAdminNote(
    url = NoteUrls(domain = domain, publicId = publicId).noteId,
    contentHtml = contentHtml,
    publishedAt = publishedAt.epochSecond,
)

internal fun StoredNote.toPublicGraphqlResponse(domain: String): QlNote = QlNote(
    url = NoteUrls(domain = domain, publicId = publicId).noteId,
    contentHtml = contentHtml,
    publishedAt = publishedAt.epochSecond,
)
