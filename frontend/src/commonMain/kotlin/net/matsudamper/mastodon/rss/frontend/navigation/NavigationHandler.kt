package net.matsudamper.mastodon.rss.frontend.navigation

import androidx.compose.runtime.Stable

@Stable
fun interface NavigationHandler {
    fun navigate(screen: Screen)
}
