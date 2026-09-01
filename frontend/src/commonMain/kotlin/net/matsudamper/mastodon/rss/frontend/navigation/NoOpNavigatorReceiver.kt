package net.matsudamper.mastodon.rss.frontend.navigation

internal object NoOpNavigatorReceiver : NavigatorReceiver {
    override fun navigateToHome() = Unit

    override fun navigateToAdmin() = Unit

    override fun navigateToAdminAccounts() = Unit

    override fun navigateToAdminAccountNew() = Unit

    override fun navigateToAdminAccount(username: String) = Unit

    override fun navigateToAccount(username: String) = Unit
}
