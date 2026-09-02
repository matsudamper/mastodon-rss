package net.matsudamper.mastodon.rss.frontend.navigation

internal class WasmNavigator(
    private val navController: NavController,
) : Navigator {
    override suspend fun navigate(screen: Screen) {
        navController.navigateTo(screen)
    }
}
