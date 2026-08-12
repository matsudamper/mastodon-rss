package net.matsudamper.mastodon.rss.graphql.resolver

import net.matsudamper.mastodon.rss.graphql.GraphQlContext
import net.matsudamper.mastodon.rss.graphql.model.QlAdminSession

internal fun GraphQlContext.toQlAdminSession(): QlAdminSession {
    return QlAdminSession(
        loggedIn = isAdminLoggedIn(),
        passwordConfigured = adminPasswordConfigured,
    )
}
