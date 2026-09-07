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
 * アクターのプロフィール画像。Actor JSON の `icon` が指す先。
 *
 * 中身はフィードが名乗っているアイコンで、こちらが取り直して返す。相手にこの URL を
 * 渡しておくと、フィードを差し替えてもアイコンの URL は変わらない。
 * 公開画面から見ても同じオリジンなので、配信元の CORS の設定に左右されない。
 */
fun Route.actorIconRoutes(
    directory: ActorDirectory,
    icons: ActorIcons,
) {
    get("/users/{username}/icon") {
        val requested = call.parameters["username"]
        val urls = directory.resolve(requested)

        // 後からアカウントやアイコンが増えれば同じ URL で出るようになる。
        // 見に来た側が 404 を持っていると、出るようになった後も出ない
        if (urls == null) {
            call.response.header(HttpHeaders.CacheControl, NO_STORE)
            call.respondText("アクターが見つからない: $requested", status = HttpStatusCode.NotFound)
            return@get
        }

        val icon = icons.find(urls.username)
        if (icon == null) {
            call.response.header(HttpHeaders.CacheControl, NO_STORE)
            call.respondText("アイコンが無い: ${urls.username}", status = HttpStatusCode.NotFound)
            return@get
        }

        val requestedVersion = call.request.queryParameters[VERSION_PARAMETER]
        call.response.header(HttpHeaders.CacheControl, icon.cacheControl(requestedVersion))
        call.response.header(CONTENT_TYPE_OPTIONS_HEADER, CONTENT_TYPE_OPTIONS)
        call.respondBytes(bytes = icon.bytes, contentType = icon.contentType)
    }
}

/**
 * アクターのプロフィール画像の引き先。
 *
 * どこから取ってくるかは Actor 側の関心ではないので、
 * [StoredActorNames] と同じく名前を渡して引けることだけを決めておく。
 */
interface ActorIcons {
    /**
     * 名前で引く。アイコンが無いか、取ってこられなければ null。
     *
     * 渡すのは [StoredActorNames] が返した保存側の綴り。
     */
    suspend fun find(username: String): ActorIcon?
}

/**
 * プロフィール画像の中身。
 *
 * @param contentType 取得元が名乗った種類。そのまま返す
 * @param version 取得元の URL から決まる値。要求された値と一致すれば入れ替わらない中身と分かる
 * @param cacheFor 取得元から決まる値が付いていない URL で来たときに持たせる時間。0 なら持たせない
 */
class ActorIcon(
    val bytes: ByteArray,
    val contentType: ContentType,
    val version: String,
    val cacheFor: Duration,
)

/**
 * 見に来た側に持たせる時間。
 *
 * [ActorUrls.icon] と GraphQL が渡す URL には取得元から決まる値が付いている。
 * その値が今置いてあるものと一致していれば、同じ URL の中身は入れ替わらない
 * （入れ替わるときは URL ごと変わる）ので長く持たせる。
 *
 * 付いていない URL は取り込みが入れ替えると中身が変わるので、
 * 配信元が言ってきた時間だけにする。長く持たせると古い画像が出続ける
 */
private fun ActorIcon.cacheControl(requestedVersion: String?): String {
    if (requestedVersion == version) return IMMUTABLE

    val seconds = cacheFor.seconds
    if (seconds <= 0) return NO_STORE
    return "public, max-age=$seconds"
}

/** 1 年。`immutable` を見ない側でも取り直しに来なくなるだけの長さ */
private const val IMMUTABLE = "public, max-age=31536000, immutable"
private const val VERSION_PARAMETER = "v"
private const val NO_STORE = "no-store"
private const val CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options"
private const val CONTENT_TYPE_OPTIONS = "nosniff"
