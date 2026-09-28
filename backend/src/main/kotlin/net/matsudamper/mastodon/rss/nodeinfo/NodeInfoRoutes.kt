package net.matsudamper.mastodon.rss.nodeinfo

import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import net.matsudamper.mastodon.rss.http.respondEndpoint

internal fun Route.nodeInfoRoutes(domain: String) {
    val endpoint = NodeInfoEndpoint(domain)
    get("/.well-known/nodeinfo") {
        call.respondEndpoint(endpoint.discovery())
    }
    get("/nodeinfo/2.1") {
        call.respondEndpoint(endpoint.nodeInfo())
    }
}
