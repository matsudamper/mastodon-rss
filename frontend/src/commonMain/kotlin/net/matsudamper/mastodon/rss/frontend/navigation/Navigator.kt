package net.matsudamper.mastodon.rss.frontend.navigation

import androidx.compose.runtime.Stable

@Stable
interface Navigator {
    suspend fun navigate(screen: Screen)
}
