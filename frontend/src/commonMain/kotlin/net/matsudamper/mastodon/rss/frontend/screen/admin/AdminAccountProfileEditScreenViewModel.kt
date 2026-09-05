package net.matsudamper.mastodon.rss.frontend.screen.admin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.mastodon.rss.frontend.event.EventSender
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminAccountResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminApi
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminUpdateAccountProfileResult

class AdminAccountProfileEditScreenViewModel(
    private val username: String,
    private val viewModelScope: CoroutineScope,
    private val api: AdminApi = AdminApi(),
) {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val state = MutableStateFlow(ViewModelState())
    private val listener = object : AdminAccountProfileEditScreenUiState.Listener {
        override fun onDisplayNameChanged(text: String) = state.update { it.copy(displayName = text, errorMessage = null) }
        override fun onSummaryChanged(text: String) = state.update { it.copy(summary = text, errorMessage = null) }
        override fun onClickSave() = save()
        override fun onClickClose() = close()
    }
    val uiStateFlow: StateFlow<AdminAccountProfileEditScreenUiState> = MutableStateFlow(createUiState(state.value)).also { ui ->
        viewModelScope.launch { state.collect { value -> ui.value = createUiState(value) } }
    }.asStateFlow()

    init {
        viewModelScope.launch {
            when (val account = api.account(username)) {
                is AdminAccountResult.Success -> state.update {
                    it.copy(displayName = account.account?.displayName.orEmpty(), summary = account.account?.summary.orEmpty())
                }
                is AdminAccountResult.Failure -> state.update { it.copy(errorMessage = account.message) }
            }
        }
    }

    private fun createUiState(value: ViewModelState) = AdminAccountProfileEditScreenUiState(
        displayName = value.displayName,
        summary = value.summary,
        inputEnabled = !value.saving,
        saving = value.saving,
        saveButtonEnabled = !value.saving,
        closeEnabled = !value.saving,
        errorMessage = value.errorMessage,
        listener = listener,
    )

    private fun save() {
        if (state.value.saving) return
        state.update { it.copy(saving = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = api.updateAccountProfile(username, state.value.displayName, state.value.summary)) {
                is AdminUpdateAccountProfileResult.Success -> events.send { it.close() }
                is AdminUpdateAccountProfileResult.Rejected -> state.update { it.copy(saving = false, errorMessage = result.toMessage()) }
                is AdminUpdateAccountProfileResult.Failure -> state.update { it.copy(saving = false, errorMessage = result.message) }
            }
        }
    }

    private fun close() {
        if (!state.value.saving) viewModelScope.launch { events.send { it.close() } }
    }

    private fun AdminUpdateAccountProfileResult.Rejected.toMessage(): String = when {
        unknownAccount -> "このアカウントは無い"
        displayNameMaxLength != null -> "表示名は ${displayNameMaxLength} 文字まで"
        summaryMaxLength != null -> "説明文は ${summaryMaxLength} 文字まで"
        else -> "保存できなかった"
    }

    private data class ViewModelState(
        val displayName: String = "",
        val summary: String = "",
        val saving: Boolean = false,
        val errorMessage: String? = null,
    )

    interface Event { suspend fun close() }
}
