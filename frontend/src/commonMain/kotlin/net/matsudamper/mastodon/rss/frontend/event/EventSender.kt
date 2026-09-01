package net.matsudamper.mastodon.rss.frontend.event

import androidx.compose.runtime.Stable
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

@Stable
class EventSender<Receiver> {
    private val receiverChannel = Channel<suspend (Receiver) -> Unit>(Channel.UNLIMITED)

    suspend fun <R> send(block: suspend (Receiver) -> R): R {
        val scope = CoroutineScope(currentCoroutineContext())
        return suspendCancellableCoroutine { continuation ->
            scope.launch {
                receiverChannel.send { receiver ->
                    continuation.resume(block(receiver))
                }
            }
        }
    }

    fun asHandler(): EventHandler<Receiver> {
        return EventHandler(receiverChannel)
    }
}
