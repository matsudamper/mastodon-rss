package net.matsudamper.mastodon.rss.frontend.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import net.matsudamper.mastodon.rss.frontend.event.EventHandler
import net.matsudamper.mastodon.rss.frontend.event.EventSender

@Composable
internal fun rememberNavigationEvents(): EventSender<NavigationHandler> {
    return remember { EventSender() }
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

@Composable
internal fun CollectScreenNavigationEvents(
    screenHandler: EventHandler<NavigationHandler>,
    appEvents: EventSender<NavigationHandler>,
) {
    LaunchedEffect(screenHandler, appEvents) {
        screenHandler.collect(
            object : NavigationHandler {
                override fun navigate(screen: Screen) {
                    launch {
                        appEvents.send { it.navigate(screen) }
                    }
                }

            },
        )
    }
}
