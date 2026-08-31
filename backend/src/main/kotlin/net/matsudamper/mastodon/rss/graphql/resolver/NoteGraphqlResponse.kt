package net.matsudamper.mastodon.rss.graphql.resolver

import net.matsudamper.mastodon.rss.graphql.model.QlAdminNote
import net.matsudamper.mastodon.rss.note.NoteUrls
import net.matsudamper.mastodon.rss.note.StoredNote

internal fun StoredNote.toGraphqlResponse(domain: String): QlAdminNote = QlAdminNote(
    id = publicId,
    url = NoteUrls(domain = domain, publicId = publicId).noteUrl,
    contentHtml = contentHtml,
    publishedAt = publishedAt.epochSecond,
)
