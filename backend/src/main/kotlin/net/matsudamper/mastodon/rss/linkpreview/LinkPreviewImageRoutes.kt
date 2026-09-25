package net.matsudamper.mastodon.rss.linkpreview

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * リンク先の OGP 画像。画面のリンクプレビューが引きに来る。
 */
internal fun Route.linkPreviewImageRoutes(images: LinkPreviewImageService) {
    get(LinkPreviewImageUrls.PATH) {
        val parameters = call.request.queryParameters
        val notePublicId = parameters[LinkPreviewImageUrls.NOTE_PARAMETER]
        val linkIndex = parameters[LinkPreviewImageUrls.LINK_PARAMETER]?.toIntOrNull()
        val version = parameters[LinkPreviewImageUrls.VERSION_PARAMETER]

        // 知らないクエリが付いた URL は受けない。画面が組み立てる URL は 1 つに決まる。
        // 余分なクエリを付けるだけで前段のキャッシュを外せると、そのたびに
        // 取得元へ取りに行くことになる
        if (
            notePublicId.isNullOrEmpty() ||
            linkIndex == null ||
            linkIndex < 0 ||
            version.isNullOrEmpty() ||
            parameters.names() != ALLOWED_PARAMETERS
        ) {
            call.response.header(HttpHeaders.CacheControl, NO_STORE)
            call.respondText("投稿とリンクの位置と版だけを指定する", status = HttpStatusCode.BadRequest)
            return@get
        }

        val image = images.find(notePublicId = notePublicId, linkIndex = linkIndex, version = version)
        if (image == null) {
            // 取得元が落ちているだけのこともあるので、短い間だけ持たせる。
            // 持たせないと、出ない画像のぶんだけ開くたびに取りに行くことになる
            call.response.header(HttpHeaders.CacheControl, "public, max-age=${NOT_FOUND_MAX_AGE.inWholeSeconds}")
            call.respondText("画像が無い", status = HttpStatusCode.NotFound)
            return@get
        }

        call.response.header(HttpHeaders.CacheControl, image.cacheControl())
        call.response.header(CONTENT_TYPE_OPTIONS_HEADER, CONTENT_TYPE_OPTIONS)
        call.respondBytes(bytes = image.bytes, contentType = image.contentType)
    }
}

/**
 * 見に来た側と前段の CDN に持たせる時間。
 *
 * 同じ URL のまま中身だけ差し替える実装もあるので `immutable` にはしない。
 * 取得元が言ってきた時間に従い、言っていなければ 1 日。長い側は 30 日で切る。
 */
private fun LinkPreviewImage.cacheControl(): String {
    val seconds = (freshFor ?: DEFAULT_MAX_AGE).coerceAtMost(MAX_MAX_AGE).inWholeSeconds
    if (seconds <= 0) return NO_STORE
    return "public, max-age=$seconds"
}

private val ALLOWED_PARAMETERS = setOf(
    LinkPreviewImageUrls.NOTE_PARAMETER,
    LinkPreviewImageUrls.LINK_PARAMETER,
    LinkPreviewImageUrls.VERSION_PARAMETER,
)
private const val NO_STORE = "no-store"
private val DEFAULT_MAX_AGE: Duration = 1.days
private val MAX_MAX_AGE: Duration = 30.days
private val NOT_FOUND_MAX_AGE: Duration = 10.minutes
private const val CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options"
private const val CONTENT_TYPE_OPTIONS = "nosniff"
