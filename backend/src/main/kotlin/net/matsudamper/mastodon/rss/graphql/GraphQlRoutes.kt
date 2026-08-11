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
 * `variables` は [JsonObject] のまま受ける。中身の型はスキーマ側で決まるので、
 * ここで `@Serializable` な型に落とすと、問い合わせを増やすたびに受け皿の型が要る。
 * 素の値に開くのは実行の直前（[toRawValue]）。
 */
@Serializable
data class GraphQlRequest(
    val query: String,
    val operationName: String? = null,
    val variables: JsonObject? = null,
)

/** 本文を読めなかったときに返すもの。GraphQL の実行に入る前なので errors の形にはならない */
@Serializable
private data class GraphQlBadRequest(
    val message: String,
)

/**
 * GraphQL を受けるパス。
 *
 * どの URL で受けるかはサーバーの都合で、スキーマの一部ではない。
 * `:shared:graphql` には置かず、口を持っているこちらで決める。
 */
const val GRAPHQL_PATH: String = "/graphql"

/**
 * 受け付けるボディの上限。
 *
 * 認証は問い合わせの中のフィールドで見るので、この口自体は誰でも叩ける。
 * 上限が無いと、`receiveText()` が読み切るまで際限なくメモリに載る。
 * inbox と同じ 1 MiB にしてある。問い合わせ 1 つがこの大きさになることは無い。
 */
private const val MAX_BODY_BYTES = 1024 * 1024

/**
 * 口は [GRAPHQL_PATH] の 1 つだけ。
 *
 * 管理用とそれ以外はフィールドで分け、認可もフィールドごとに見る。
 * エンドポイントを分けると、認可の有無が URL と実装の 2 か所に散る。
 *
 * ActivityPub 側（WebFinger・Actor・inbox）はここに載せない。相手の実装が
 * 決まっている REST なので、こちらの都合で形を変えられない。
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

        // データフェッチャーは同期で、パスワードの照合では PBKDF2 を 21 万回まわす。
        // そのまま実行すると、その間このスレッドが他のリクエストを処理できない
        val result = withContext(Dispatchers.IO) { engine.execute(request, call) }

        // 実行時のエラーは errors に入って 200 で返る。GraphQL の仕様どおり
        call.respondJson(JsonObject.serializer(), result)
    }
}
