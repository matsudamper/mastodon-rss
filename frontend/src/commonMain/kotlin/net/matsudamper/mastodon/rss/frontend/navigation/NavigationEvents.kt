package net.matsudamper.mastodon.rss.frontend.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import net.matsudamper.mastodon.rss.frontend.event.EventSender

@Composable
fun rememberNavigationEvents(): EventSender<NavigatorReceiver> {
    return remember { EventSender() }
}

@Composable
fun CollectNavigationEvents(
    events: EventSender<NavigatorReceiver>,
    receiver: NavigatorReceiver,
) {
    LaunchedEffect(events, receiver) {
        events.asHandler().collect(receiver)
    }
}
