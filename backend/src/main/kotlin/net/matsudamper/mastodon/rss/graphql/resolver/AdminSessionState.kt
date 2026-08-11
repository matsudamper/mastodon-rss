package net.matsudamper.mastodon.rss.graphql.resolver

import io.ktor.server.application.ApplicationCall
import net.matsudamper.mastodon.rss.admin.AdminSessions
import net.matsudamper.mastodon.rss.admin.sessionToken
import net.matsudamper.mastodon.rss.crypto.PasswordHash
import net.matsudamper.mastodon.rss.graphql.model.QlAdminSession

/**
 * リクエストの Cookie からログインの状態を組み立てる。
 *
 * `session` も `login` の失敗も `logout` も同じものを返すので 1 か所にまとめる。
 * `passwordConfigured` が false のときは入力欄を出さない側の判断材料になるため、
 * ログインの成否と一緒に返す必要がある。
 */
internal fun adminSession(
    call: ApplicationCall,
    passwordHash: PasswordHash?,
    sessions: AdminSessions,
): QlAdminSession {
    return QlAdminSession(
        loggedIn = sessions.isValid(call.sessionToken()),
        passwordConfigured = passwordHash != null,
    )
}
