package net.matsudamper.mastodon.rss.actor

import io.ktor.http.HttpHeaders
import io.ktor.server.request.header
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import net.matsudamper.mastodon.rss.http.respondEndpoint
import net.matsudamper.mastodon.rss.url.WebPageUrls

internal fun Route.actorRoutes(
    directory: ActorDirectory,
    actorKey: ActorKey,
    feedLinks: StoredFeedLinks,
    profiles: StoredActorProfiles,
    webPages: WebPageUrls?,
) {
    val endpoint = ActorEndpoint(directory, actorKey, feedLinks, profiles, webPages)
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
