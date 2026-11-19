package net.matsudamper.mastodon.rss.graphql

import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import net.matsudamper.mastodon.rss.admin.AdminSessionCookieManager
import net.matsudamper.mastodon.rss.admin.AdminSessionInMemoryStore
import net.matsudamper.mastodon.rss.crypto.PasswordHash

/**
 * 通信に関係するものを入れておく
 */
class GraphQlContext(
    call: ApplicationCall,
    private val sessionStore: AdminSessionInMemoryStore,
    cookieSecure: Boolean,
    trustCfConnectingIp: Boolean,
) {
    val clientIp: String =
        call.request.headers["CF-Connecting-IP"]?.takeIf { trustCfConnectingIp }
            ?: call.request.origin.remoteAddress

    private val cookie = AdminSessionCookieManager(call = call, secure = cookieSecure)

    fun isAdminLoggedIn(): Boolean = sessionStore.isValid(cookie.token())

    /**
     * Cookieを発行する
     * 発行した Cookie はまだリクエスト側に無いので、[isAdminLoggedIn] は false のまま
     */
    fun issueAdminSession() {
        cookie.append(token = sessionStore.create(), maxAgeSeconds = sessionStore.ttlSeconds)
    }

    fun clearAdminSession() {
        sessionStore.remove(cookie.token())
        cookie.expire()
    }
}
