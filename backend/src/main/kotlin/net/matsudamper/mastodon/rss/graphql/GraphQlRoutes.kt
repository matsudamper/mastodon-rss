package net.matsudamper.mastodon.rss.graphql

import kotlinx.serialization.json.JsonObject
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import net.matsudamper.mastodon.rss.graphql.data.GraphQlBadRequest
import net.matsudamper.mastodon.rss.graphql.data.GraphQlRequest
import net.matsudamper.mastodon.rss.json.AppJson
import net.matsudamper.mastodon.rss.json.respondJson
import net.matsudamper.mastodon.rss.shared.GRAPHQL_PATH

private const val MAX_BODY_BYTES = 1024 * 1024

fun Route.graphQlRoutes(engine: GraphQlEngine) {
    post(GRAPHQL_PATH) {
        // 読んでから確かめても、その時点で受け取り終えている
        val declaredLength = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
        if (declaredLength != null && declaredLength > MAX_BODY_BYTES) {
            call.respondText("ボディが大きすぎる", status = HttpStatusCode.PayloadTooLarge)
            return@post
        }

        val body = call.receiveText()
        if (body.length > MAX_BODY_BYTES) {
            call.respondText("ボディが大きすぎる", status = HttpStatusCode.PayloadTooLarge)
            return@post
        }

        val request =
            runCatching { AppJson.decodeFromString(GraphQlRequest.serializer(), body) }
                .getOrElse {
                    call.respondJson(
                        GraphQlBadRequest.serializer(),
                        GraphQlBadRequest("GraphQL のリクエストとして読めない"),
                        status = HttpStatusCode.BadRequest,
                    )
                    return@post
                }

        // パスワードの照合で PBKDF2 を回すので、そのまま実行すると他が詰まる
        val result = engine.execute(request, call)

        call.respondJson(JsonObject.serializer(), result)
    }
}
