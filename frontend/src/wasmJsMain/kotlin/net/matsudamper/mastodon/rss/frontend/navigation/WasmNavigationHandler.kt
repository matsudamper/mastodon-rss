package net.matsudamper.mastodon.rss.frontend.navigation

internal class WasmNavigationHandler(
    private val navigator: Navigator,
) : NavigationHandler {
    override fun navigate(screen: Screen) {
        navigator.navigateTo(screen)
    }
}
