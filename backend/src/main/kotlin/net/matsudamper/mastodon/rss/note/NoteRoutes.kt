package net.matsudamper.mastodon.rss.note

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.collection.COLLECTION_CURSOR_PARAM
import net.matsudamper.kotpub.entity.PublicNoteId
import net.matsudamper.kotpub.note.FeaturedEndpoint
import net.matsudamper.kotpub.note.NoteEndpoint
import net.matsudamper.kotpub.note.NoteStore
import net.matsudamper.kotpub.note.OutboxEndpoint
import net.matsudamper.kotpub.url.WebPageUrls
import net.matsudamper.mastodon.rss.http.respondEndpoint
import net.matsudamper.mastodon.rss.logic.IgnoredBoosts
import net.matsudamper.mastodon.rss.shared.WebPagePath
import net.matsudamper.mastodon.rss.staticfiles.StaticFiles
import net.matsudamper.mastodon.rss.webpage.PublicPageResponse

internal fun Route.noteRoutes(
    domain: String,
    notes: NoteStore,
    webPages: WebPageUrls?,
    staticFiles: StaticFiles?,
) {
    val endpoint = NoteEndpoint(domain, notes, webPages)
    get("/notes/{publicId}") {
        val publicId = call.parameters["publicId"].orEmpty()
        val note = notes.find(PublicNoteId(publicId))
        if (note == null) {
            PublicPageResponse.vary(call)
            call.respondText("見つからない", status = HttpStatusCode.NotFound)
        } else if (PublicPageResponse.wantsJson(call.request.header(HttpHeaders.Accept))) {
            PublicPageResponse.vary(call)
            PublicPageResponse.alternate(call, "https://$domain/notes/$publicId")
            call.respondEndpoint(endpoint.get(publicId, PublicPageResponse.JSON_ACCEPT))
        } else {
            call.response.header(HttpHeaders.Vary, "Origin, Accept")
            call.respondRedirect(WebPagePath.accountNote(note.username, publicId), permanent = true)
        }
    }
    listOf("", "/").forEach { trailing ->
        get("/${WebPagePath.ACCOUNT_PREFIX}{username}/{publicId}$trailing") {
            PublicPageResponse.vary(call)
            val publicId = call.parameters["publicId"].orEmpty()
            val note = notes.find(PublicNoteId(publicId))
            if (note == null || !note.username.equals(call.parameters["username"], ignoreCase = true)) {
                call.respondText("見つからない", status = HttpStatusCode.NotFound)
            } else {
                PublicPageResponse.alternate(call, "https://$domain/notes/$publicId")
                if (PublicPageResponse.wantsJson(call.request.header(HttpHeaders.Accept))) {
                    call.respondEndpoint(endpoint.get(publicId, PublicPageResponse.JSON_ACCEPT))
                } else {
                    PublicPageResponse.html(call, staticFiles)
                }
            }
        }
    }
}

internal fun Route.outboxRoutes(
    directory: ActorDirectory,
    notes: NoteStore,
    webPages: WebPageUrls?,
) {
    val endpoint = OutboxEndpoint(directory, notes, IgnoredBoosts.outbox, webPages)
    get("/users/{username}/outbox") {
        call.respondEndpoint(
            endpoint.get(
                username = call.parameters["username"],
                accept = call.request.header(HttpHeaders.Accept),
                cursor = call.request.queryParameters[COLLECTION_CURSOR_PARAM],
            ),
        )
    }
}

internal fun Route.featuredRoutes(
    directory: ActorDirectory,
    webPages: WebPageUrls?,
) {
    // 投稿をプロフィールに固定する機能は無い
    val endpoint = FeaturedEndpoint(directory, { _, _ -> listOf() }, webPages)
    get("/users/{username}/collections/featured") {
        call.respondEndpoint(
            endpoint.get(username = call.parameters["username"], accept = call.request.header(HttpHeaders.Accept)),
        )
    }
}
