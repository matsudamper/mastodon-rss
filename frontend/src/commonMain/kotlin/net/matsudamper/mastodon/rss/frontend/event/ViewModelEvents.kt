package net.matsudamper.mastodon.rss.frontend.event

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
internal fun <Event> CollectViewModelEvents(
    handler: EventHandler<Event>,
    receiver: Event,
) {
    LaunchedEffect(handler, receiver) {
        handler.collect(receiver)
    }
}
