package net.matsudamper.mastodon.rss.graphql.resolver

import io.ktor.server.application.ApplicationCall
import net.matsudamper.mastodon.rss.admin.AdminSessions
import net.matsudamper.mastodon.rss.admin.sessionToken
import net.matsudamper.mastodon.rss.crypto.PasswordHash
import net.matsudamper.mastodon.rss.graphql.model.QlAdminSession

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
