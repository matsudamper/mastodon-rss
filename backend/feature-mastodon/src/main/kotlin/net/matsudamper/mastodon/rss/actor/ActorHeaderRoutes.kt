package net.matsudamper.mastodon.rss.actor

import java.time.Duration
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * アクターのプロフィールヘッダー。Actor JSON の `image` が指す先。
 *
 * 中身はフィードや配信元ページが名乗っている画像で、こちらが取り直して返す。相手にこの URL を
 * 渡しておくと、名乗っている画像が差し替わってもヘッダーの URL は変わらない。
 */
fun Route.actorHeaderRoutes(
    directory: ActorDirectory,
    headers: ActorHeaders,
) {
    get("/users/{username}/header") {
        val requested = call.parameters["username"]
        val urls = directory.resolve(requested)

        // 後からアカウントやヘッダーが増えれば同じ URL で出るようになる。
        // 見に来た側が 404 を持っていると、出るようになった後も出ない
        if (urls == null) {
            call.response.header(HttpHeaders.CacheControl, NO_STORE)
            call.respondText("アクターが見つからない: $requested", status = HttpStatusCode.NotFound)
            return@get
        }

        val header = headers.find(urls.username)
        if (header == null) {
            call.response.header(HttpHeaders.CacheControl, NO_STORE)
            call.respondText("ヘッダーが無い: ${urls.username}", status = HttpStatusCode.NotFound)
            return@get
        }

        val requestedVersion = call.request.queryParameters[VERSION_PARAMETER]
        call.response.header(HttpHeaders.CacheControl, header.cacheControl(requestedVersion))
        call.response.header(CONTENT_TYPE_OPTIONS_HEADER, CONTENT_TYPE_OPTIONS)
        call.respondBytes(bytes = header.bytes, contentType = header.contentType)
    }
}

/**
 * アクターのプロフィールヘッダーの引き先。
 *
 * どこから取ってくるかは Actor 側の関心ではないので、
 * [StoredActorNames] と同じく名前を渡して引けることだけを決めておく。
 */
interface ActorHeaders {
    /**
     * 名前で引く。ヘッダーが無ければ null。
     *
     * 渡すのは [StoredActorNames] が返した保存側の綴り。
     */
    suspend fun find(username: String): ActorHeader?
}

/**
 * プロフィールヘッダーの中身。
 *
 * @param contentType 取得元が名乗った種類。そのまま返す
 * @param version 中身から決まる値。要求された値と一致すれば同じ中身と分かる
 * @param cacheFor 見に来た側に持たせる時間。0 なら持たせない
 */
class ActorHeader(
    val bytes: ByteArray,
    val contentType: ContentType,
    val version: String,
    val cacheFor: Duration,
)

/**
 * 見に来た側に持たせる時間。
 *
 * [ActorUrls.header] が渡す URL には中身から決まる値が付いている。その値が今置いてあるものと
 * 一致していれば、同じ URL の中身は入れ替わらない（入れ替わるときは URL ごと変わる）ので長く持たせる。
 *
 * ただし [cacheFor] が尽きているときは値が一致していても持たせない。中身は次の取り込みで
 * 入れ替わるので、そこで長く持たせると入れ替わる直前の画像が 1 年出続ける
 */
private fun ActorHeader.cacheControl(requestedVersion: String?): String {
    val seconds = cacheFor.seconds
    if (seconds <= 0) return NO_STORE
    if (requestedVersion == version) return IMMUTABLE
    return "public, max-age=$seconds"
}

/**
 * 1 年。`immutable` を見ない側でも取り直しに来なくなるだけの長さ
 */
private const val IMMUTABLE = "public, max-age=31536000, immutable"
private const val VERSION_PARAMETER = "v"
private const val NO_STORE = "no-store"
private const val CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options"
private const val CONTENT_TYPE_OPTIONS = "nosniff"
