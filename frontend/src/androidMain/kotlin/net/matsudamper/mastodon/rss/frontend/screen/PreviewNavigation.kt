package net.matsudamper.mastodon.rss.frontend.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import net.matsudamper.mastodon.rss.frontend.navigation.CollectNavigationEvents
import net.matsudamper.mastodon.rss.frontend.navigation.LocalNavigationEvents
import net.matsudamper.mastodon.rss.frontend.navigation.NoOpNavigatorReceiver
import net.matsudamper.mastodon.rss.frontend.navigation.rememberNavigationEvents

@Composable
internal fun PreviewNavigation(content: @Composable () -> Unit) {
    val navigationEvents = rememberNavigationEvents()
    CollectNavigationEvents(events = navigationEvents, receiver = NoOpNavigatorReceiver)

    CompositionLocalProvider(LocalNavigationEvents provides navigationEvents) {
        content()
    }
}
