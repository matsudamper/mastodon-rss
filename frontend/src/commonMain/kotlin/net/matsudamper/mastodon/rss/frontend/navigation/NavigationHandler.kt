package net.matsudamper.mastodon.rss.frontend.navigation

import androidx.compose.runtime.Stable

@Stable
interface NavigationHandler {
    fun navigate(screen: Screen)
}
