package net.matsudamper.mastodon.rss.graphql

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import net.matsudamper.mastodon.rss.json.AppJson
import net.matsudamper.mastodon.rss.json.respondJson

/**
 * GraphQL の本文。
 *
 * `variables` を [JsonObject] のまま受けるのは、`@Serializable` な型に落とすと
 * 問い合わせを増やすたびに受け皿の型が要るため。開くのは実行の直前（[toRawValue]）。
 */
@Serializable
data class GraphQlRequest(
    val query: String,
    val operationName: String? = null,
    val variables: JsonObject? = null,
)

/** 実行に入る前の失敗。errors の形にはならない */
@Serializable
private data class GraphQlBadRequest(
    val message: String,
)

/** どの URL で受けるかはサーバーの都合なので、スキーマとは別にこちらが持つ */
const val GRAPHQL_PATH: String = "/graphql"

/** 認可はフィールドで見るので口自体は誰でも叩ける。上限が無いと際限なくメモリに載る */
private const val MAX_BODY_BYTES = 1024 * 1024

/**
 * 口は [GRAPHQL_PATH] の 1 つだけ。管理用とそれ以外はフィールドで分け、
 * 認可もフィールドごとに見る。ActivityPub 側は相手の実装が決まっているので載せない。
 */
fun Route.graphQlRoutes(engine: GraphQlEngine) {
    post(GRAPHQL_PATH) {
        // 読む前に長さで弾く。読んでから確かめても、その時点で受け取り終えている
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

        // パスワードの照合で PBKDF2 を 21 万回まわす。同期のまま実行すると他が詰まる
        val result = withContext(Dispatchers.IO) { engine.execute(request, call) }

        // 実行時のエラーは errors に入って 200 で返る
        call.respondJson(JsonObject.serializer(), result)
    }
}
