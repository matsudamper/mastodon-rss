package net.matsudamper.mastodon.rss.frontend.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import net.matsudamper.mastodon.rss.frontend.event.EventSender
import net.matsudamper.mastodon.rss.frontend.navigation.CollectNavigationEvents
import net.matsudamper.mastodon.rss.frontend.navigation.NavigatorReceiver
import net.matsudamper.mastodon.rss.frontend.navigation.rememberNavigationEvents

@Composable
internal fun rememberPreviewNavigationEvents(): EventSender<NavigatorReceiver> {
    val navigationEvents = rememberNavigationEvents()
    CollectNavigationEvents(events = navigationEvents, receiver = NavigatorReceiver { })
    return remember(navigationEvents) { navigationEvents }
}
