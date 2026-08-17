package net.matsudamper.mastodon.rss.note

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import net.matsudamper.mastodon.rss.activitypub.ActivityPubContentTypes
import net.matsudamper.mastodon.rss.actor.ActorDirectory
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.collection.COLLECTION_CURSOR_PARAM
import net.matsudamper.mastodon.rss.collection.COLLECTION_PAGE_SIZE
import net.matsudamper.mastodon.rss.collection.OrderedCollection
import net.matsudamper.mastodon.rss.collection.OrderedCollectionPage
import net.matsudamper.mastodon.rss.json.respondJson

/**
 * 相手が `Create` の `object.id` をパーマリンクとして引きに来る先
 */
fun Route.noteRoutes(
    domain: String,
    notes: NoteStore,
) {
    get("/notes/{publicId}") {
        val publicId = call.parameters["publicId"]
        val note = publicId?.let { notes.find(it) }

        if (note == null) {
            call.respondText("投稿が見つからない: $publicId", status = HttpStatusCode.NotFound)
            return@get
        }

        call.respondJson(
            serializer = Note.serializer(),
            value = noteDocument(
                urls = ActorUrls(domain = domain, username = note.username),
                note = note,
                embedded = false,
            ),
            contentType = ActivityPubContentTypes.negotiate(call.request.header(HttpHeaders.Accept)),
        )
    }
}

/**
 * Actor の `outbox` が指している先。
 *
 * 並べるのは投稿ではなく配信したときと同じ `Create`。`outbox` は
 * アクティビティの記録であって投稿の一覧ではない、というのが ActivityPub の決まり。
 */
fun Route.outboxRoutes(
    directory: ActorDirectory,
    notes: NoteStore,
) {
    get("/users/{username}/outbox") {
        val requested = call.parameters["username"]
        val urls = directory.resolve(requested)
        if (urls == null) {
            call.respondText("アカウントが見つからない: $requested", status = HttpStatusCode.NotFound)
            return@get
        }

        val contentType = ActivityPubContentTypes.negotiate(call.request.header(HttpHeaders.Accept))
        val total = notes.count(urls.username)
        val cursor = call.request.queryParameters[COLLECTION_CURSOR_PARAM]

        // パラメータが無ければ集合そのもの。空文字でも付いていれば先頭のページ
        if (cursor == null) {
            call.respondJson(
                serializer = OrderedCollection.serializer(),
                value = OrderedCollection(
                    id = urls.outbox,
                    totalItems = total,
                    first = pageUrl(urls, null),
                ),
                contentType = contentType,
            )
            return@get
        }

        val page = notes.list(
            username = urls.username,
            after = cursor.ifEmpty { null }?.let { NoteCursor.decode(it) },
            limit = COLLECTION_PAGE_SIZE,
        )

        val items = page
            .map { note ->
                CreateNote(
                    id = NoteUrls(domain = urls.domain, publicId = note.publicId).createId,
                    actor = urls.actorId,
                    published = note.publishedAt.toString(),
                    to = listOf(PUBLIC_AUDIENCE),
                    cc = listOf(urls.followers),
                    target = noteDocument(urls = urls, note = note, embedded = true),
                )
            }

        call.respondJson(
            serializer = OrderedCollectionPage.serializer(CreateNote.serializer()),
            value = OrderedCollectionPage(
                id = pageUrl(urls, cursor.ifEmpty { null }?.let { NoteCursor.decode(it) }),
                totalItems = total,
                partOf = urls.outbox,
                orderedItems = items,
                next = if (page.size < COLLECTION_PAGE_SIZE) null else pageUrl(urls, page.last().cursor),
            ),
            contentType = contentType,
        )
    }
}

/**
 * @param after 直前のページの最後の位置。null なら先頭のページ
 */
private fun pageUrl(
    urls: ActorUrls,
    after: NoteCursor?,
): String = "${urls.outbox}?$COLLECTION_CURSOR_PARAM=${after?.encode()?.encodeURLParameter().orEmpty()}"

/**
 * @param embedded `Create` に包む場合は `@context` を入れない。外側が持っている
 */
private fun noteDocument(
    urls: ActorUrls,
    note: StoredNote,
    embedded: Boolean,
): Note {
    val noteUrls = NoteUrls(domain = urls.domain, publicId = note.publicId)

    return Note(
        context = if (embedded) null else OrderedCollection.DEFAULT_CONTEXT,
        id = noteUrls.noteId,
        attributedTo = urls.actorId,
        content = note.contentHtml,
        published = note.publishedAt.toString(),
        to = listOf(PUBLIC_AUDIENCE),
        cc = listOf(urls.followers),
        url = noteUrls.noteId,
    )
}
