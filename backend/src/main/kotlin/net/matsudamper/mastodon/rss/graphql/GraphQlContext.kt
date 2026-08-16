package net.matsudamper.mastodon.rss.graphql

import io.ktor.server.application.ApplicationCall
import net.matsudamper.mastodon.rss.admin.AdminSessionCookieManager
import net.matsudamper.mastodon.rss.admin.AdminSessionInMemoryStore
import net.matsudamper.mastodon.rss.crypto.PasswordHash

/**
 * リクエスト 1 つぶんの入れ物。リゾルバは [ApplicationCall] を直接持たず、ここを通す。
 * 渡すとヘッダもボディもレスポンスも触れるが、要るのはセッションの読み書きだけ
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
        // Cookie を消すだけだと、値を控えられていた場合に使い続けられる
        sessionStore.remove(cookie.token())
        cookie.expire()
    }
}
