package net.matsudamper.mastodon.rss.remoteactor

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * フォロワーのアイコン。画面のフォロワー一覧が引きに来る。
 *
 * アクター文書の URL をクエリで受けて、記録にある相手のものだけを中継する。
 */
internal fun Route.remoteActorIconRoutes(icons: RemoteActorIconService) {
    get(RemoteActorIconUrls.PATH) {
        val actorUri = call.request.queryParameters[RemoteActorIconUrls.ACTOR_PARAMETER]
        val version = call.request.queryParameters[RemoteActorIconUrls.VERSION_PARAMETER]
        if (actorUri.isNullOrEmpty() || version.isNullOrEmpty()) {
            call.response.header(HttpHeaders.CacheControl, NO_STORE)
            call.respondText("アクターと版の指定が無い", status = HttpStatusCode.BadRequest)
            return@get
        }

        val icon = icons.find(actorUri = actorUri, version = version)
        if (icon == null) {
            // 配信元が落ちているだけのこともあるので、短い間だけ持たせる。
            // 持たせないと、出ない相手のぶんだけ開くたびに取りに行くことになる
            call.response.header(HttpHeaders.CacheControl, "public, max-age=$NOT_FOUND_MAX_AGE_SECONDS")
            call.respondText("アイコンが無い: $actorUri", status = HttpStatusCode.NotFound)
            return@get
        }

        call.response.header(HttpHeaders.CacheControl, icon.cacheControl())
        call.response.header(CONTENT_TYPE_OPTIONS_HEADER, CONTENT_TYPE_OPTIONS)
        call.respondBytes(bytes = icon.bytes, contentType = icon.contentType)
    }
}

/**
 * 見に来た側と前段の CDN に持たせる時間。
 *
 * 取得元の URL が変われば画面の URL も変わるが、同じ URL のまま中身だけ
 * 差し替える実装もあるので `immutable` にはしない。配信元が言ってきた時間に従い、
 * 言っていなければ 1 時間。長い側は 1 日で切る。
 */
private fun RemoteActorIcon.cacheControl(): String {
    val seconds = (freshFor?.seconds ?: DEFAULT_MAX_AGE_SECONDS).coerceAtMost(MAX_MAX_AGE_SECONDS)
    if (seconds <= 0) return NO_STORE
    return "public, max-age=$seconds"
}

private const val NO_STORE = "no-store"
private const val DEFAULT_MAX_AGE_SECONDS = 60L * 60
private const val MAX_MAX_AGE_SECONDS = 24L * 60 * 60
private const val NOT_FOUND_MAX_AGE_SECONDS = 60L * 10
private const val CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options"
private const val CONTENT_TYPE_OPTIONS = "nosniff"
