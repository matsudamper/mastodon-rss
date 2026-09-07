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

/** Actor JSON の `image` が指すプロフィールヘッダー */
fun Route.actorHeaderRoutes(
    directory: ActorDirectory,
    headers: ActorHeaders,
) {
    get("/users/{username}/header") {
        val requested = call.parameters["username"]
        val urls = directory.resolve(requested)

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

/** アクターのプロフィールヘッダーの引き先 */
interface ActorHeaders {
    suspend fun find(username: String): ActorHeader?
}

class ActorHeader(
    val bytes: ByteArray,
    val contentType: ContentType,
    val version: String,
    val cacheFor: Duration,
)

private fun ActorHeader.cacheControl(requestedVersion: String?): String {
    if (requestedVersion == version) return IMMUTABLE

    val seconds = cacheFor.seconds
    if (seconds <= 0) return NO_STORE
    return "public, max-age=$seconds"
}

private const val IMMUTABLE = "public, max-age=31536000, immutable"
private const val VERSION_PARAMETER = "v"
private const val NO_STORE = "no-store"
private const val CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options"
private const val CONTENT_TYPE_OPTIONS = "nosniff"
