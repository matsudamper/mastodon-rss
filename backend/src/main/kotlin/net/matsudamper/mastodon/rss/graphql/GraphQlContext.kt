package net.matsudamper.mastodon.rss.graphql

import io.ktor.server.application.ApplicationCall
import net.matsudamper.mastodon.rss.admin.AdminSessions
import net.matsudamper.mastodon.rss.admin.appendSessionCookie
import net.matsudamper.mastodon.rss.admin.expireSessionCookie
import net.matsudamper.mastodon.rss.admin.sessionToken
import net.matsudamper.mastodon.rss.crypto.PasswordHash

/**
 * リクエスト 1 つぶんの入れ物。リゾルバは [ApplicationCall] を直接持たず、ここを通す。
 * 渡すとヘッダもボディもレスポンスも触れるが、要るのはセッションの読み書きだけ
 */
class GraphQlContext(
    private val call: ApplicationCall,
    private val passwordHash: PasswordHash?,
    private val sessions: AdminSessions,
    private val cookieSecure: Boolean,
) {
    val adminPasswordConfigured: Boolean = passwordHash != null

    fun isAdminLoggedIn(): Boolean = sessions.isValid(call.sessionToken())

    fun matchesAdminPassword(password: String): Boolean = passwordHash?.matches(password) == true

    /** 発行した Cookie はまだリクエスト側に無いので、[isAdminLoggedIn] は false のまま */
    fun issueAdminSession() {
        call.appendSessionCookie(
            token = sessions.create(),
            maxAgeSeconds = sessions.ttlSeconds,
            secure = cookieSecure,
        )
    }

    fun clearAdminSession() {
        // Cookie を消すだけだと、値を控えられていた場合に使い続けられる
        sessions.remove(call.sessionToken())
        call.expireSessionCookie(secure = cookieSecure)
    }
}
