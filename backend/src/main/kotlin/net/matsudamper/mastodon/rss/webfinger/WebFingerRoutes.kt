package net.matsudamper.mastodon.rss.webfinger

import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import net.matsudamper.mastodon.rss.actor.ActorDirectory
import net.matsudamper.mastodon.rss.http.respondEndpoint

internal fun Route.webFingerRoutes(directory: ActorDirectory) {
    val endpoint = WebFingerEndpoint(directory)
    get("/.well-known/webfinger") {
        call.respondEndpoint(endpoint.get(resource = call.request.queryParameters["resource"]))
    }
}
