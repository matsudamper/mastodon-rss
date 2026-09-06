package net.matsudamper.mastodon.rss.actor

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

        // 配信元に毎回取りに行かないよう、見に来た側に持たせる。
        // 差し替えてもすぐには反映されないが、頻繁に変わるものではない
        call.response.header(HttpHeaders.CacheControl, CACHE_CONTROL)
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
 */
class ActorIcon(
    val bytes: ByteArray,
    val contentType: ContentType,
)

private const val CACHE_CONTROL = "public, max-age=3600"
