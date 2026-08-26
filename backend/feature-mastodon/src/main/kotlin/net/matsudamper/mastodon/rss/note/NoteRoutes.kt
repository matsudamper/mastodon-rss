package net.matsudamper.mastodon.rss.note

import java.time.Instant
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
import net.matsudamper.mastodon.rss.collection.FEATURED_COLLECTION_SIZE
import net.matsudamper.mastodon.rss.collection.OrderedCollection
import net.matsudamper.mastodon.rss.collection.OrderedCollectionPage
import net.matsudamper.mastodon.rss.collection.OrderedCollectionWithItems
import net.matsudamper.mastodon.rss.json.respondJson

/**
 * 配信した投稿を返す。
 *
 * 相手は受け取った `Create` の `object.id` をパーマリンクとして引きに来る。
 * ここが 404 だと、タイムラインには出ていても開けない投稿になる。
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
 * 配信した投稿の一覧を返す。Actor の `outbox` が指している先。
 *
 * 中身は投稿そのものではなく、配信したときと同じ `Create` を並べる。
 * `outbox` はアクティビティの記録であって投稿の一覧ではない、というのが
 * ActivityPub の決まり。
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
            // 読めない cursor は先頭に倒す。相手が辿るだけの値なので、
            // 壊れていることを教えても直しようが無い
            after = cursor.ifEmpty { null }?.let { decodeCursor(it) },
            limit = COLLECTION_PAGE_SIZE,
        )

        val items = page
            .map { note ->
                CreateNote(
                    id = NoteUrls(domain = urls.domain, publicId = note.publicId).createId,
                    actor = urls.actorId,
                    published = note.publishedAt.toActivityPubPublished(),
                    to = listOf(PUBLIC_AUDIENCE),
                    cc = listOf(urls.followers),
                    target = noteDocument(urls = urls, note = note, embedded = true),
                )
            }

        call.respondJson(
            serializer = OrderedCollectionPage.serializer(CreateNote.serializer()),
            value = OrderedCollectionPage(
                id = pageUrl(urls, cursor.ifEmpty { null }?.let { decodeCursor(it) }),
                totalItems = total,
                partOf = urls.outbox,
                orderedItems = items,
                // 総数ではなく取れた件数で判断する。読んでいる間に増えていることがある
                next = if (page.size < COLLECTION_PAGE_SIZE) null else pageUrl(urls, page.last().position),
            ),
            contentType = contentType,
        )
    }
}

/**
 * プロフィールに載せる投稿の一覧。Actor の `featured` が指している先。
 *
 * Mastodon は未フォローでもプロフィールを開いたときここを引きに来る。
 * outbox はフォロー後のバックフィル向けで、未フォローのプロフィール表示には使われない。
 */
fun Route.featuredRoutes(
    directory: ActorDirectory,
    notes: NoteStore,
) {
    get("/users/{username}/collections/featured") {
        val requested = call.parameters["username"]
        val urls = directory.resolve(requested)
        if (urls == null) {
            call.respondText("アカウントが見つからない: $requested", status = HttpStatusCode.NotFound)
            return@get
        }

        val total = notes.count(urls.username)
        val items = notes
            .list(username = urls.username, after = null, limit = FEATURED_COLLECTION_SIZE)
            .map { note -> noteDocument(urls = urls, note = note, embedded = true) }

        call.respondJson(
            serializer = OrderedCollectionWithItems.serializer(Note.serializer()),
            value = OrderedCollectionWithItems(
                id = urls.featured,
                totalItems = total,
                orderedItems = items,
            ),
            contentType = ActivityPubContentTypes.negotiate(call.request.header(HttpHeaders.Accept)),
        )
    }
}

/**
 * @param after 直前のページの最後の位置。null なら先頭のページ
 */
private fun pageUrl(
    urls: ActorUrls,
    after: NotePosition?,
): String = "${urls.outbox}?$COLLECTION_CURSOR_PARAM=${after?.encodeCursor()?.encodeURLParameter().orEmpty()}"

/**
 * 相手が辿るだけの値なので、読める形にしておく必要は無い。
 * 区切りは `_`。`publicId` は UUID なので混ざらない
 */
private fun NotePosition.encodeCursor(): String = "${publishedAt.epochSecond}_${publishedAt.nano}_$publicId"

/**
 * 読めない形なら null。壊れた cursor は先頭に倒す。
 * 相手に教えても直しようが無いので、拒否はしない
 */
private fun decodeCursor(raw: String): NotePosition? {
    val parts = raw.split('_', limit = 3)
    if (parts.size != 3) return null

    val epochSecond = parts[0].toLongOrNull() ?: return null
    val nano = parts[1].toLongOrNull() ?: return null
    if (parts[2].isEmpty()) return null

    return runCatching {
        NotePosition(publishedAt = Instant.ofEpochSecond(epochSecond, nano), publicId = parts[2])
    }.getOrNull()
}

/**
 * 保存した投稿を返す形に直す。
 *
 * @param embedded `Create` に包む場合は `@context` を入れない。外側が持っているので、
 *   重ねると同じものを 2 回書くことになる
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
        published = note.publishedAt.toActivityPubPublished(),
        to = listOf(PUBLIC_AUDIENCE),
        cc = listOf(urls.followers),
        atomUri = noteUrls.noteId,
        url = noteUrls.noteId,
    )
}
