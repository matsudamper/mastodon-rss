package net.matsudamper.mastodon.rss.frontend.navigation

import androidx.compose.runtime.Stable

@Stable
interface Navigator {
    fun navigate(screen: Screen)
}
