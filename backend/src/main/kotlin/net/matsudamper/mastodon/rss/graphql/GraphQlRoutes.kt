package net.matsudamper.mastodon.rss.graphql

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import net.matsudamper.mastodon.rss.json.AppJson
import net.matsudamper.mastodon.rss.json.respondJson
import net.matsudamper.mastodon.rss.shared.GRAPHQL_PATH

/**
 * `variables` を [JsonObject] のまま受けるのは、型が問い合わせごとに変わるため
 */
@Serializable
data class GraphQlRequest(
    val query: String,
    val operationName: String? = null,
    val variables: JsonObject? = null,
)

@Serializable
private data class GraphQlBadRequest(
    val message: String,
)

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
