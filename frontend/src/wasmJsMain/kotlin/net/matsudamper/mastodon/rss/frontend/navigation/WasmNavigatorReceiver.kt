package net.matsudamper.mastodon.rss.frontend.navigation

import net.matsudamper.mastodon.rss.frontend.navigation.Screen as AppScreen

internal class WasmNavigatorReceiver(
    private val navigator: Navigator,
) : NavigatorReceiver {
    override fun navigateToHome() {
        navigator.navigateTo(AppScreen.Home)
    }

    override fun navigateToAdmin() {
        navigator.navigateTo(AppScreen.Admin)
    }

    override fun navigateToAdminAccounts() {
        navigator.navigateTo(AppScreen.AdminAccounts)
    }

    override fun navigateToAdminAccountNew() {
        navigator.navigateTo(AppScreen.AdminAccountNew)
    }

    override fun navigateToAdminAccount(username: String) {
        navigator.navigateTo(AppScreen.AdminAccount(username))
    }

    override fun navigateToAccount(username: String) {
        navigator.navigateTo(AppScreen.Account(username))
    }
}
