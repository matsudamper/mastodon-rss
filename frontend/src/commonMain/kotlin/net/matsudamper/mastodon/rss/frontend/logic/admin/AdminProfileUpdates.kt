package net.matsudamper.mastodon.rss.frontend.logic.admin

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object AdminProfileUpdates {
    private val updatedUsernameFlow = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val updatedUsernames: Flow<String> = updatedUsernameFlow.asSharedFlow()

    fun notifyUpdated(username: String) {
        updatedUsernameFlow.tryEmit(username)
    }
}
