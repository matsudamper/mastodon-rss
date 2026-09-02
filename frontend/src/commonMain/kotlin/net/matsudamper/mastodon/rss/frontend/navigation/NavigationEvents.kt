package net.matsudamper.mastodon.rss.frontend.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import net.matsudamper.mastodon.rss.frontend.event.EventHandler
import net.matsudamper.mastodon.rss.frontend.event.EventSender

@Composable
internal fun rememberNavigationEvents(): EventSender<Navigator> {
    return remember { EventSender() }
}

@Composable
internal fun CollectNavigationEvents(
    events: EventSender<Navigator>,
    handler: Navigator,
) {
    LaunchedEffect(events, handler) {
        events.asHandler().collect(handler)
    }
}

@Composable
internal fun CollectScreenNavigationEvents(
    screenHandler: EventHandler<Navigator>,
    appEvents: EventSender<Navigator>,
) {
    LaunchedEffect(screenHandler, appEvents) {
        screenHandler.collect(
            object : Navigator {
                override suspend fun navigate(screen: Screen) {
                    appEvents.send { it.navigate(screen) }
                }
            },
        )
    }
}
