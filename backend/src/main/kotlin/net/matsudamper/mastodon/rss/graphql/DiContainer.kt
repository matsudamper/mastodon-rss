package net.matsudamper.mastodon.rss.graphql

import net.matsudamper.mastodon.rss.crypto.PasswordHash
import net.matsudamper.mastodon.rss.logic.AdminLoginService

class DiContainer(
    passwordHash: PasswordHash?,
) {
    val adminLoginService: AdminLoginService = AdminLoginService(passwordHash)
}
