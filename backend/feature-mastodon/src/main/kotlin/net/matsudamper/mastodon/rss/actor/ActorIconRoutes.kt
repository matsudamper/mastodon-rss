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

        if (urls == null) {
            call.respondText("アクターが見つからない: $requested", status = HttpStatusCode.NotFound)
            return@get
        }

        val icon = icons.find(urls.username)
        if (icon == null) {
            call.respondText("アイコンが無い: ${urls.username}", status = HttpStatusCode.NotFound)
            return@get
        }

        // 持たせる時間は配信元の言い分に合わせる。ここで長く持たせると、
        // こちらが取り直した後も見に来た側には古いものが出続ける
        call.response.header(HttpHeaders.CacheControl, icon.cacheControl())
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
 * @param cacheFor 見に来た側に持たせてよい時間。0 なら持たせない
 */
class ActorIcon(
    val bytes: ByteArray,
    val contentType: ContentType,
    val cacheFor: Duration,
)

private fun ActorIcon.cacheControl(): String {
    val seconds = cacheFor.seconds
    if (seconds <= 0) return NO_STORE
    return "public, max-age=$seconds"
}

private const val NO_STORE = "no-store"
private const val CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options"
private const val CONTENT_TYPE_OPTIONS = "nosniff"
