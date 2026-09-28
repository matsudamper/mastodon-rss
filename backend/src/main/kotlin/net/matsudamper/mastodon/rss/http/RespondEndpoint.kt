package net.matsudamper.mastodon.rss.http

import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import net.matsudamper.activitypub.http.EndpointResponse
import net.matsudamper.activitypub.http.RequestHeaders

/**
 * エンドポイントが返した応答をそのまま書き出す
 */
internal suspend fun ApplicationCall.respondEndpoint(endpointResponse: EndpointResponse) {
    endpointResponse.headers.forEach { (name, value) -> response.header(name, value) }
    respondBytes(
        bytes = endpointResponse.body,
        contentType = ContentType.parse(endpointResponse.contentType),
        status = HttpStatusCode.fromValue(endpointResponse.status),
    )
}

/**
 * Ktor のヘッダを [RequestHeaders] として渡す。大文字小文字を区別しないのは Ktor 側と同じ
 */
internal class KtorRequestHeaders(
    private val headers: Headers,
) : RequestHeaders {
    override fun getAll(name: String): List<String>? = headers.getAll(name)
}
