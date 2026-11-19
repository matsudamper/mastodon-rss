package net.matsudamper.mastodon.rss.graphql

import kotlinx.io.readByteArray
import kotlinx.serialization.json.JsonObject
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.header
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.readRemaining
import net.matsudamper.kotpub.json.AppJson
import net.matsudamper.mastodon.rss.graphql.data.GraphQlBadRequest
import net.matsudamper.mastodon.rss.graphql.data.GraphQlRequest
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

        // Content-Length の無いチャンク転送もあるので、上限 +1 バイトで読むのを止めて超過を見分ける
        val bodyChannel = call.receiveChannel()
        val bodyBytes = bodyChannel.readRemaining(MAX_BODY_BYTES + 1L).readByteArray()
        // readRemaining は受信エラーで閉じても読めた分を返すので、途中で切れた本文を実行しないよう再送出する
        val bodyReceiveError = bodyChannel.closedCause
        if (bodyReceiveError != null) throw bodyReceiveError
        if (bodyBytes.size > MAX_BODY_BYTES) {
            call.respondText("ボディが大きすぎる", status = HttpStatusCode.PayloadTooLarge)
            return@post
        }
        val body = bodyBytes.decodeToString()

        val request = runCatching {
            AppJson.decodeFromString(GraphQlRequest.serializer(), body)
        }
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
