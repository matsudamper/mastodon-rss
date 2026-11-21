package net.matsudamper.mastodon.rss.webpage

import kotlin.io.path.readText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parseHeaderValue
import io.ktor.http.withCharset
import io.ktor.server.application.ApplicationCall
import io.ktor.server.http.content.LocalFileContent
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import net.matsudamper.mastodon.rss.staticfiles.StaticFiles

internal object PublicPageResponse {
    const val JSON_ACCEPT: String = "application/activity+json"

    fun wantsJson(accept: String?): Boolean {
        val jsonTypes = setOf("application/json", "application/activity+json", "application/ld+json")
        val preferred = parseHeaderValue(accept.orEmpty())
            .filter { it.quality > 0 }
            .sortedByDescending { it.quality }
            .firstOrNull { it.value.lowercase() in jsonTypes + setOf("text/html", "*/*") }
        return preferred?.value?.lowercase() in jsonTypes
    }

    fun vary(call: ApplicationCall) {
        call.response.header(HttpHeaders.Vary, "Accept")
    }

    fun alternate(call: ApplicationCall, url: String) {
        call.response.header(HttpHeaders.Link, "<$url>; rel=\"alternate\"; type=\"application/activity+json\"")
    }

    /**
     * @param ogp 他のサービスに貼ったときのカードの中身。出さないなら null
     */
    suspend fun html(
        call: ApplicationCall,
        staticFiles: StaticFiles?,
        ogp: PageOgp?,
    ) {
        val index = staticFiles?.index()
        if (index == null) {
            call.respondText("見つからない: ${call.request.path()}", status = HttpStatusCode.NotFound)
            return
        }
        call.response.header(HttpHeaders.CacheControl, "no-store")
        if (ogp == null) {
            call.respond(LocalFileContent(index.toFile(), ContentType.Text.Html))
        } else {
            val indexHtml = withContext(Dispatchers.IO) { index.readText() }
            call.respondText(PageOgpHtml.inject(indexHtml = indexHtml, ogp = ogp), ContentType.Text.Html.withCharset(Charsets.UTF_8))
        }
    }
}
