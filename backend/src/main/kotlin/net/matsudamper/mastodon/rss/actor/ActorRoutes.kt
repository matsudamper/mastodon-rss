package net.matsudamper.mastodon.rss.actor

import io.ktor.http.HttpHeaders
import io.ktor.server.request.header
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.ActorEndpoint
import net.matsudamper.kotpub.actor.ActorHeaderEndpoint
import net.matsudamper.kotpub.actor.ActorHeaders
import net.matsudamper.kotpub.actor.ActorIconEndpoint
import net.matsudamper.kotpub.actor.ActorIcons
import net.matsudamper.kotpub.actor.ActorPrivateKey
import net.matsudamper.kotpub.actor.StoredActorAppearances
import net.matsudamper.kotpub.actor.StoredActorProfiles
import net.matsudamper.kotpub.actor.StoredActorPublicKeys
import net.matsudamper.kotpub.url.WebPageUrls
import net.matsudamper.mastodon.rss.http.respondEndpoint

internal fun Route.actorRoutes(
    directory: ActorDirectory,
    actorKey: ActorPrivateKey,
    appearances: StoredActorAppearances,
    profiles: StoredActorProfiles,
    webPages: WebPageUrls?,
) {
    val publicKeys = object : StoredActorPublicKeys {
        override fun find(username: String): String = actorKey.publicKeyPem
    }
    val endpoint = ActorEndpoint(directory, publicKeys, appearances, profiles, webPages)
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
