package net.matsudamper.mastodon.rss.frontend.navigation

fun interface NavigationHandler {
    fun navigate(screen: Screen)
}
