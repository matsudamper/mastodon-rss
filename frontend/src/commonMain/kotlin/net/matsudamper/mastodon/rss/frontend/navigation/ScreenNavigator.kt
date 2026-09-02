package net.matsudamper.mastodon.rss.frontend.navigation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.matsudamper.mastodon.rss.frontend.event.EventSender

class ScreenNavigator(
    private val events: EventSender<NavigationHandler>,
    private val scope: CoroutineScope,
) {
    fun navigate(screen: Screen) {
        scope.launch {
            events.send { handler ->
                handler.navigate(screen)
            }
        }
    }
}
