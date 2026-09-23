package net.matsudamper.mastodon.rss.linkpreview

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * リンク先の OGP 画像。画面の投稿のリンクカードが引きに来る
 */
internal fun Route.linkPreviewImageRoutes(images: LinkPreviewImageService) {
    get(LinkPreviewImageUrls.PATH) {
        val parameters = call.request.queryParameters
        val pageUrl = parameters[LinkPreviewImageUrls.PAGE_PARAMETER]
        val version = parameters[LinkPreviewImageUrls.VERSION_PARAMETER]

        // 知らないクエリが付いた URL は受けない。余分なクエリを付けるだけで
        // 前段のキャッシュを外せると、そのたびに配信元へ取りに行くことになる
        if (pageUrl.isNullOrEmpty() || version.isNullOrEmpty() || parameters.names() != ALLOWED_PARAMETERS) {
            call.response.header(HttpHeaders.CacheControl, NO_STORE)
            call.respondText("ページと版だけを指定する", status = HttpStatusCode.BadRequest)
            return@get
        }

        val image = images.find(pageUrl = pageUrl, version = version)
        if (image == null) {
            // 配信元が落ちているだけのこともあるので、短い間だけ持たせる
            call.response.header(HttpHeaders.CacheControl, "public, max-age=$NOT_FOUND_MAX_AGE_SECONDS")
            call.respondText("画像が無い: $pageUrl", status = HttpStatusCode.NotFound)
            return@get
        }

        call.response.header(HttpHeaders.CacheControl, image.cacheControl())
        call.response.header(CONTENT_TYPE_OPTIONS_HEADER, CONTENT_TYPE_OPTIONS)
        call.respondBytes(bytes = image.bytes, contentType = image.contentType)
    }
}

/**
 * 同じ URL のまま中身だけ差し替えるサイトもあるので `immutable` にはしない。
 * 配信元が言ってきた時間に従い、言っていなければ 1 時間。長い側は 1 日で切る
 */
private fun LinkPreviewImage.cacheControl(): String {
    val seconds = (freshFor?.seconds ?: DEFAULT_MAX_AGE_SECONDS).coerceAtMost(MAX_MAX_AGE_SECONDS)
    if (seconds <= 0) return NO_STORE
    return "public, max-age=$seconds"
}

private val ALLOWED_PARAMETERS = setOf(LinkPreviewImageUrls.PAGE_PARAMETER, LinkPreviewImageUrls.VERSION_PARAMETER)
private const val NO_STORE = "no-store"
private const val DEFAULT_MAX_AGE_SECONDS = 60L * 60
private const val MAX_MAX_AGE_SECONDS = 24L * 60 * 60
private const val NOT_FOUND_MAX_AGE_SECONDS = 60L * 10
private const val CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options"
private const val CONTENT_TYPE_OPTIONS = "nosniff"
