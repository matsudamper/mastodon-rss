package net.matsudamper.mastodon.rss.frontend.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.matsudamper.mastodon.rss.frontend.event.EventSender

val LocalNavigationEvents = compositionLocalOf<EventSender<NavigatorReceiver>> {
    error("NavigationEvents が未設定")
}

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

@Composable
fun rememberNavigation(): Navigation {
    val events = LocalNavigationEvents.current
    val scope = rememberCoroutineScope()
    return remember(events, scope) {
        Navigation(events, scope)
    }
}

class Navigation internal constructor(
    private val events: EventSender<NavigatorReceiver>,
    private val scope: CoroutineScope,
) {
    fun navigate(block: NavigatorReceiver.() -> Unit) {
        scope.launch {
            events.send { receiver ->
                receiver.block()
            }
        }
    }
}
