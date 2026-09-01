package net.matsudamper.mastodon.rss.frontend.navigation

import androidx.compose.runtime.Stable

@Stable
fun interface NavigatorReceiver {
    fun navigate(screen: Screen)
}
