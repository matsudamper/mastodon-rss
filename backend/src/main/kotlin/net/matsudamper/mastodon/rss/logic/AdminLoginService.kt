package net.matsudamper.mastodon.rss.logic

import net.matsudamper.mastodon.rss.crypto.PasswordHash

class AdminLoginService(
    private val passwordHash: PasswordHash?,
) {
    val adminPasswordConfigured: Boolean = passwordHash != null

    fun matchesAdminPassword(password: String): Boolean = passwordHash?.matches(password) == true
}
