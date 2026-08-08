package net.matsudamper.mastodon.rss.webfinger

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import net.matsudamper.mastodon.rss.activitypub.ActivityPubContentTypes
import net.matsudamper.mastodon.rss.actor.ActorDirectory
import net.matsudamper.mastodon.rss.json.respondJson

/**
 * WebFinger (RFC 7033) のエンドポイント。アカウント発見の 1 ホップ目。
 *
 * `resource` は必須のクエリパラメータなので、無ければ 400。
 * 知らない相手なら 404 で、ここで 200 を返してしまうと Mastodon 側に
 * 空のアカウントがあるように見える。
 */
fun Route.webFingerRoutes(directory: ActorDirectory) {
    get("/.well-known/webfinger") {
        val resource = call.request.queryParameters["resource"]

        if (resource.isNullOrBlank()) {
            call.respondText(
                "resource クエリパラメータが必要（例: ?resource=acct:admin@example.com）",
                status = HttpStatusCode.BadRequest,
            )
            return@get
        }

        val urls = directory.resolveResource(resource)
        if (urls == null) {
            call.respondText("該当する resource が無い: $resource", status = HttpStatusCode.NotFound)
            return@get
        }

        call.respondJson(
            serializer = WebFingerResponse.serializer(),
            value =
                WebFingerResponse(
                    // 要求された綴りではなく正規の acct を返す。
                    // 大文字小文字が混ざったまま返すと相手側の突き合わせで揺れる
                    subject = urls.acct,
                    aliases = listOf(urls.actorId),
                    links =
                        listOf(
                            WebFingerLink(
                                rel = WebFingerLink.REL_SELF,
                                type = ActivityPubContentTypes.ActivityJson.toString(),
                                href = urls.actorId,
                            ),
                        ),
                ),
            contentType = ActivityPubContentTypes.JrdJson,
        )
    }
}
