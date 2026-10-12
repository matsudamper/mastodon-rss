package net.matsudamper.mastodon.rss.follower

import io.ktor.http.HttpHeaders
import io.ktor.server.request.header
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.collection.COLLECTION_CURSOR_PARAM
import net.matsudamper.kotpub.follower.FollowerEndpoint
import net.matsudamper.kotpub.follower.FollowerStore
import net.matsudamper.mastodon.rss.http.respondEndpoint

internal fun Route.followerRoutes(
    directory: ActorDirectory,
    followers: FollowerStore,
) {
    val endpoint = FollowerEndpoint(directory, followers)
    get("/users/{username}/followers") {
        call.respondEndpoint(
            endpoint.get(
                username = call.parameters["username"],
                accept = call.request.header(HttpHeaders.Accept),
                cursor = call.request.queryParameters[COLLECTION_CURSOR_PARAM],
            ),
        )
    }
}
