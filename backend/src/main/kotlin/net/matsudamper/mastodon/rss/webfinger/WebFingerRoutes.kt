package net.matsudamper.mastodon.rss.webfinger

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.InstanceActorUrls
import net.matsudamper.kotpub.webfinger.WebFingerEndpoint
import net.matsudamper.mastodon.rss.http.respondEndpoint

internal fun Route.webFingerRoutes(directory: ActorDirectory, instanceActor: InstanceActorUrls) {
    val endpoint = WebFingerEndpoint(directory, instanceActor)
    get("/.well-known/webfinger") {
        val resource = call.request.queryParameters["resource"]
        // サーバー自身のアクター（/actor）はまだ返さない。WebFinger で見つかっても Actor を取得できないので、アカウントに当たらなければ無いと答える
        if (!resource.isNullOrBlank() && directory.resolveResource(resource) == null) {
            call.respondText("該当する resource が無い: $resource", status = HttpStatusCode.NotFound)
            return@get
        }
        call.respondEndpoint(endpoint.get(resource = resource))
    }
}
