package net.matsudamper.mastodon.rss.frontend.navigation

import androidx.compose.runtime.Stable

@Stable
interface NavigatorReceiver {
    fun navigateToHome()

    fun navigateToAdmin()

    fun navigateToAdminAccounts()

    fun navigateToAdminAccountNew()

    fun navigateToAdminAccount(username: String)

    fun navigateToAccount(username: String)
}
