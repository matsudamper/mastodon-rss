package net.matsudamper.mastodon.rss.frontend.screen.account

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.mastodon.rss.frontend.event.EventSender
import net.matsudamper.mastodon.rss.frontend.format.UnixTimeUtil
import net.matsudamper.mastodon.rss.frontend.logic.account.AccountApi
import net.matsudamper.mastodon.rss.frontend.logic.account.AccountNoteResult

class AccountNoteScreenViewModel(
    private val username: String,
    private val noteId: String,
    private val viewModelScope: CoroutineScope,
    private val api: AccountApi = AccountApi(),
) {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()

    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState())

    private var noteJob: Job? = null

    // uiStateFlow より後ろに置くと、初期値を組み立てる時点でまだ入っていない
    private val listener = object : AccountNoteScreenUiState.Listener {
        override fun onClickClose() {
            viewModelScope.launch { events.send { it.close() } }
        }

        override fun onClickReload() {
            reload()
        }

        override fun onClickActivityPub() {
            val note = (viewModelStateFlow.value.note as? AccountNoteResult.Success)?.note ?: return
            viewModelScope.launch { events.send { it.openExternalLink(note.url) } }
        }
    }

    val uiStateFlow: StateFlow<AccountNoteScreenUiState> =
        MutableStateFlow(createUiState(ViewModelState()))
            .also { uiStateFlow ->
                viewModelScope.launch {
                    viewModelStateFlow.collect { viewModelState ->
                        uiStateFlow.update { createUiState(viewModelState) }
                    }
                }
            }.asStateFlow()

    fun onStart() {
        reload()
    }

    private fun reload() {
        noteJob?.cancel()
        viewModelStateFlow.update { it.copy(note = null) }
        noteJob = viewModelScope.launch {
            val result = api.note(username = username, id = noteId)
            viewModelStateFlow.update { it.copy(note = result) }
        }
    }

    private fun createUiState(state: ViewModelState): AccountNoteScreenUiState {
        val content = when (val result = state.note) {
            null -> AccountNoteScreenUiState.Content.Loading

            AccountNoteResult.NotFound -> AccountNoteScreenUiState.Content.NotFound

            is AccountNoteResult.Failure -> AccountNoteScreenUiState.Content.Error(result.message)

            is AccountNoteResult.Success -> AccountNoteScreenUiState.Content.Loaded(
                contentHtml = result.note.contentHtml,
                publishedAt = UnixTimeUtil.format(result.note.publishedAt.epochSeconds),
            )
        }

        return AccountNoteScreenUiState(
            content = content,
            listener = listener,
        )
    }

    private data class ViewModelState(
        val note: AccountNoteResult? = null,
    )

    interface Event {
        /**
         * 1 画面として積んでいるので、戻るのと同じ
         */
        suspend fun close()

        suspend fun openExternalLink(url: String)
    }
}
