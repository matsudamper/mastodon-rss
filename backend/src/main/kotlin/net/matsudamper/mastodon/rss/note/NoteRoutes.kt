package net.matsudamper.mastodon.rss.note

import io.ktor.http.HttpHeaders
import io.ktor.server.request.header
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.collection.COLLECTION_CURSOR_PARAM
import net.matsudamper.kotpub.note.FeaturedEndpoint
import net.matsudamper.kotpub.note.NoteEndpoint
import net.matsudamper.kotpub.note.NoteStore
import net.matsudamper.kotpub.note.OutboxEndpoint
import net.matsudamper.kotpub.url.WebPageUrls
import net.matsudamper.mastodon.rss.http.respondEndpoint

internal fun Route.noteRoutes(
    domain: String,
    notes: NoteStore,
    webPages: WebPageUrls?,
) {
    val endpoint = NoteEndpoint(domain, notes, webPages)
    get("/notes/{publicId}") {
        call.respondEndpoint(
            endpoint.get(publicId = call.parameters["publicId"], accept = call.request.header(HttpHeaders.Accept)),
        )
    }
}

internal fun Route.outboxRoutes(
    directory: ActorDirectory,
    notes: NoteStore,
    webPages: WebPageUrls?,
) {
    val endpoint = OutboxEndpoint(directory, notes, webPages)
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

internal fun Route.featuredRoutes(directory: ActorDirectory) {
    val endpoint = FeaturedEndpoint(directory)
    get("/users/{username}/collections/featured") {
        call.respondEndpoint(
            endpoint.get(username = call.parameters["username"], accept = call.request.header(HttpHeaders.Accept)),
        )
    }
}
