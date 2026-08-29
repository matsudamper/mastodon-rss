package net.matsudamper.mastodon.rss.frontend.event

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.receiveAsFlow

class EventHandler<Receiver>(
    private val events: ReceiveChannel<suspend (Receiver) -> Unit>,
) {
    suspend fun collect(target: Receiver) {
        events.receiveAsFlow().collect { block ->
            block(target)
        }
    }
}
