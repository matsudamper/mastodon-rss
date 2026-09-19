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
fun Route.remoteActorIconRoutes(icons: RemoteActorIconService) {
    get(RemoteActorIconUrls.PATH) {
        val actorUri = call.request.queryParameters[RemoteActorIconUrls.ACTOR_PARAMETER]
        if (actorUri.isNullOrEmpty()) {
            call.response.header(HttpHeaders.CacheControl, NO_STORE)
            call.respondText("アクターの指定が無い", status = HttpStatusCode.BadRequest)
            return@get
        }

        val icon = icons.find(actorUri)
        if (icon == null) {
            // 配信元が落ちているだけのこともあるので、短い間だけ持たせる。
            // 持たせないと、出ない相手のぶんだけ開くたびに取りに行くことになる
            call.response.header(HttpHeaders.CacheControl, "public, max-age=${NOT_FOUND_MAX_AGE_SECONDS}")
            call.respondText("アイコンが無い: $actorUri", status = HttpStatusCode.NotFound)
            return@get
        }

        val requestedVersion = call.request.queryParameters[RemoteActorIconUrls.VERSION_PARAMETER]
        call.response.header(HttpHeaders.CacheControl, icon.cacheControl(requestedVersion))
        call.response.header(CONTENT_TYPE_OPTIONS_HEADER, CONTENT_TYPE_OPTIONS)
        call.respondBytes(bytes = icon.bytes, contentType = icon.contentType)
    }
}

/**
 * 見に来た側と前段の CDN に持たせる時間。
 *
 * 取得元から決まる値が付いた URL は、相手がアイコンを差し替えれば URL ごと変わる。
 * 付いていない URL で来た場合は中身が入れ替わりうるので、配信元が言ってきた時間に
 * 従い、言っていなければ短く切る。
 */
private fun RemoteActorIcon.cacheControl(requestedVersion: String?): String {
    if (requestedVersion == version) return IMMUTABLE

    val seconds = freshFor?.seconds ?: DEFAULT_MAX_AGE_SECONDS
    if (seconds <= 0) return NO_STORE
    return "public, max-age=$seconds"
}

/** 1 年。`immutable` を見ない側でも取り直しに来なくなるだけの長さ */
private const val IMMUTABLE = "public, max-age=31536000, immutable"
private const val NO_STORE = "no-store"
private const val DEFAULT_MAX_AGE_SECONDS = 60L * 60
private const val NOT_FOUND_MAX_AGE_SECONDS = 60L * 10
private const val CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options"
private const val CONTENT_TYPE_OPTIONS = "nosniff"
