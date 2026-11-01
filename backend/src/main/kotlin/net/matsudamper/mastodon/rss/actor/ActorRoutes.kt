package net.matsudamper.mastodon.rss.actor

import io.ktor.http.HttpHeaders
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.response.respondRedirect
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
import net.matsudamper.mastodon.rss.shared.WebPagePath
import net.matsudamper.mastodon.rss.staticfiles.StaticFiles
import net.matsudamper.mastodon.rss.webpage.PublicPageResponse

internal fun Route.actorRoutes(
    directory: ActorDirectory,
    actorKey: ActorPrivateKey,
    appearances: StoredActorAppearances,
    profiles: StoredActorProfiles,
    webPages: WebPageUrls?,
    staticFiles: StaticFiles?,
) {
    val publicKeys = object : StoredActorPublicKeys {
        override fun find(username: String): String = actorKey.publicKeyPem
    }
    val endpoint = ActorEndpoint(directory, publicKeys, appearances, profiles, webPages)
    get("/users/{username}") {
        if (PublicPageResponse.wantsJson(call.request.header(HttpHeaders.Accept))) {
            PublicPageResponse.vary(call)
            call.respondEndpoint(endpoint.get(call.parameters["username"], PublicPageResponse.JSON_ACCEPT))
        } else {
            call.response.header(HttpHeaders.Vary, "Origin, Accept")
            call.respondRedirect(WebPagePath.account(call.parameters["username"].orEmpty()), permanent = true)
        }
    }
    listOf("", "/").forEach { trailing ->
        get("/${WebPagePath.ACCOUNT_PREFIX}{username}$trailing") {
            PublicPageResponse.vary(call)
            val username = call.parameters["username"]
            val actor = directory.resolve(username.orEmpty())
            if (PublicPageResponse.wantsJson(call.request.header(HttpHeaders.Accept))) {
                call.respondEndpoint(endpoint.get(username, PublicPageResponse.JSON_ACCEPT))
            } else if (actor == null) {
                PublicPageResponse.html(call, null)
            } else {
                call.response.header(
                    HttpHeaders.Link,
                    "<https://${actor.domain}/.well-known/webfinger?resource=${actor.acct}>; rel=\"lrdd\"; type=\"application/jrd+json\", " +
                        "<${actor.actorId}>; rel=\"alternate\"; type=\"application/activity+json\"",
                )
                PublicPageResponse.html(call, staticFiles)
            }
        }
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
