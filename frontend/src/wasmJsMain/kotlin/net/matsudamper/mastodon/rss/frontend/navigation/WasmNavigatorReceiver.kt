package net.matsudamper.mastodon.rss.frontend.navigation

internal class WasmNavigatorReceiver(
    private val navigator: Navigator,
) : NavigatorReceiver {
    override fun navigate(screen: Screen) {
        navigator.navigateTo(screen)
    }
}
