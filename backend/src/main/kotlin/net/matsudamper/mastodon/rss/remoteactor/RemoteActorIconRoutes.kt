package net.matsudamper.mastodon.rss.remoteactor

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
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
 * フォロワーのアイコン。画面のフォロワー一覧が引きに来る。
 *
 * アクター文書の URL をクエリで受けて、記録にある相手のものだけを中継する。
 */
internal fun Route.remoteActorIconRoutes(icons: RemoteActorIconService) {
    get(RemoteActorIconUrls.PATH) {
        val parameters = call.request.queryParameters
        val actorUri = parameters[RemoteActorIconUrls.ACTOR_PARAMETER]
        val version = parameters[RemoteActorIconUrls.VERSION_PARAMETER]

        // 知らないクエリが付いた URL は受けない。画面が組み立てる URL は 1 つに決まる。
        // 余分なクエリを付けるだけで前段のキャッシュを外せると、そのたびに
        // 配信元へ取りに行くことになる
        if (actorUri.isNullOrEmpty() || version.isNullOrEmpty() || parameters.names() != ALLOWED_PARAMETERS) {
            call.response.header(HttpHeaders.CacheControl, NO_STORE)
            call.respondText("アクターと版だけを指定する", status = HttpStatusCode.BadRequest)
            return@get
        }

        val icon = icons.find(actorUri = actorUri, version = version)
        if (icon == null) {
            // 配信元が落ちているだけのこともあるので、短い間だけ持たせる。
            // 持たせないと、出ない相手のぶんだけ開くたびに取りに行くことになる
            call.response.header(HttpHeaders.CacheControl, "public, max-age=${NOT_FOUND_MAX_AGE.inWholeSeconds}")
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
    val seconds = (freshFor ?: DEFAULT_MAX_AGE).coerceAtMost(MAX_MAX_AGE).inWholeSeconds
    if (seconds <= 0) return NO_STORE
    return "public, max-age=$seconds"
}

private val ALLOWED_PARAMETERS = setOf(RemoteActorIconUrls.ACTOR_PARAMETER, RemoteActorIconUrls.VERSION_PARAMETER)
private const val NO_STORE = "no-store"
private val DEFAULT_MAX_AGE: Duration = 1.hours
private val MAX_MAX_AGE: Duration = 1.days
private val NOT_FOUND_MAX_AGE: Duration = 10.minutes
private const val CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options"
private const val CONTENT_TYPE_OPTIONS = "nosniff"
