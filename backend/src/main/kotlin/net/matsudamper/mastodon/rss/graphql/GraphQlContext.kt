package net.matsudamper.mastodon.rss.graphql

import io.ktor.server.application.ApplicationCall
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
) {
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
