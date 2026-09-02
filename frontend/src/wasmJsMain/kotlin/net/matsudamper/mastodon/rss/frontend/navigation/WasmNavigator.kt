package net.matsudamper.mastodon.rss.frontend.navigation

internal class WasmNavigator(
    private val navController: NavController,
) : Navigator {
    override fun navigate(screen: Screen) {
        navController.navigateTo(screen)
    }
}
