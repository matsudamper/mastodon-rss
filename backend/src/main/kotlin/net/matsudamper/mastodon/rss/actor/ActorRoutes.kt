package net.matsudamper.mastodon.rss.actor

import io.ktor.http.HttpHeaders
import io.ktor.server.request.header
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import net.matsudamper.activitypub.actor.ActorDirectory
import net.matsudamper.activitypub.actor.ActorEndpoint
import net.matsudamper.activitypub.actor.ActorHeaderEndpoint
import net.matsudamper.activitypub.actor.ActorHeaders
import net.matsudamper.activitypub.actor.ActorIconEndpoint
import net.matsudamper.activitypub.actor.ActorIcons
import net.matsudamper.activitypub.actor.ActorKey
import net.matsudamper.activitypub.actor.StoredActorAppearances
import net.matsudamper.activitypub.actor.StoredActorProfiles
import net.matsudamper.activitypub.url.WebPageUrls
import net.matsudamper.mastodon.rss.http.respondEndpoint

internal fun Route.actorRoutes(
    directory: ActorDirectory,
    actorKey: ActorKey,
    appearances: StoredActorAppearances,
    profiles: StoredActorProfiles,
    webPages: WebPageUrls?,
) {
    val endpoint = ActorEndpoint(directory, actorKey, appearances, profiles, webPages)
    get("/users/{username}") {
        call.respondEndpoint(
            endpoint.get(username = call.parameters["username"], accept = call.request.header(HttpHeaders.Accept)),
        )
    }
}

internal fun Route.actorIconRoutes(
    directory: ActorDirectory,
    icons: ActorIcons,
) {
    val endpoint = ActorIconEndpoint(directory, icons)
    get("/users/{username}/icon") {
        call.respondEndpoint(
            endpoint.get(username = call.parameters["username"], version = call.request.queryParameters["v"]),
        )
    }
}

internal fun Route.actorHeaderRoutes(
    directory: ActorDirectory,
    headers: ActorHeaders,
) {
    val endpoint = ActorHeaderEndpoint(directory, headers)
    get("/users/{username}/header") {
        call.respondEndpoint(
            endpoint.get(username = call.parameters["username"], version = call.request.queryParameters["v"]),
        )
    }
}
