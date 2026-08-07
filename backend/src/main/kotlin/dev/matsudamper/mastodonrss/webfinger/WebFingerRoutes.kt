package dev.matsudamper.mastodonrss.webfinger

import dev.matsudamper.mastodonrss.activitypub.ActivityPubContentTypes
import dev.matsudamper.mastodonrss.actor.ActorUrls
import dev.matsudamper.mastodonrss.json.respondJson
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * WebFinger (RFC 7033) のエンドポイント。アカウント発見の 1 ホップ目。
 *
 * `resource` は必須のクエリパラメータなので、無ければ 400。
 * 知らない相手なら 404 で、ここで 200 を返してしまうと Mastodon 側に
 * 空のアカウントがあるように見える。
 */
fun Route.webFingerRoutes(urls: ActorUrls) {
    get("/.well-known/webfinger") {
        val resource = call.request.queryParameters["resource"]

        if (resource.isNullOrBlank()) {
            call.respondText(
                "resource クエリパラメータが必要（例: ?resource=${urls.acct}）",
                status = HttpStatusCode.BadRequest,
            )
            return@get
        }

        if (!urls.matches(resource)) {
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
