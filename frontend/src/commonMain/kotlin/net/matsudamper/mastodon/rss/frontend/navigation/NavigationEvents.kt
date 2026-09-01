package net.matsudamper.mastodon.rss.frontend.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import net.matsudamper.mastodon.rss.frontend.event.EventSender

@Composable
internal fun rememberNavigationEvents(): EventSender<NavigationHandler> {
    return remember { EventSender() }
}

@Composable
internal fun rememberScreenNavigator(
    navigationEvents: EventSender<NavigationHandler>,
): ScreenNavigator {
    val viewModelScope = rememberCoroutineScope()
    return remember(navigationEvents, viewModelScope) {
        ScreenNavigator(navigationEvents, viewModelScope)
    }
}

@Composable
internal fun CollectNavigationEvents(
    events: EventSender<NavigationHandler>,
    handler: NavigationHandler,
) {
    LaunchedEffect(events, handler) {
        events.asHandler().collect(handler)
    }
}
