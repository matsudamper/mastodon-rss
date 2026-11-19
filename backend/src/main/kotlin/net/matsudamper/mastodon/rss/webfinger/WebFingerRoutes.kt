package net.matsudamper.mastodon.rss.webfinger

import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.InstanceActorUrls
import net.matsudamper.kotpub.webfinger.WebFingerEndpoint
import net.matsudamper.mastodon.rss.http.respondEndpoint

internal fun Route.webFingerRoutes(directory: ActorDirectory, instanceActor: InstanceActorUrls) {
    val endpoint = WebFingerEndpoint(directory, instanceActor)
    get("/.well-known/webfinger") {
        call.respondEndpoint(endpoint.get(resource = call.request.queryParameters["resource"]))
    }
}
