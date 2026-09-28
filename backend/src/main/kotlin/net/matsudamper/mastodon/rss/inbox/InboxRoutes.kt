package net.matsudamper.mastodon.rss.inbox

import kotlinx.io.readByteArray
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.uri
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.readRemaining
import net.matsudamper.activitypub.actor.ActorDirectory
import net.matsudamper.activitypub.actor.ActorUrls
import net.matsudamper.activitypub.inbox.InboxEndpoint
import net.matsudamper.activitypub.inbox.InboxService
import net.matsudamper.activitypub.inbox.IncomingInboxRequest
import net.matsudamper.mastodon.rss.http.KtorRequestHeaders
import net.matsudamper.mastodon.rss.http.respondEndpoint

internal fun Route.inboxRoutes(
    directory: ActorDirectory,
    service: InboxService,
) {
    val endpoint = InboxEndpoint(directory, service)
    post("/users/{username}/inbox") {
        call.respondEndpoint(
            endpoint.receiveForAccount(username = call.parameters["username"], request = call.incomingInboxRequest()),
        )
    }

    post(ActorUrls.SHARED_INBOX_PATH) {
        call.respondEndpoint(endpoint.receiveShared(call.incomingInboxRequest()))
    }
}

private fun ApplicationCall.incomingInboxRequest(): IncomingInboxRequest =
    IncomingInboxRequest(
        method = request.httpMethod.value,
        requestTarget = request.uri,
        headers = KtorRequestHeaders(request.headers),
        readBody = { maxBytes -> receiveChannel().readRemaining(maxBytes + 1L).readByteArray() },
    )
