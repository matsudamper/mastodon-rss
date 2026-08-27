package net.matsudamper.mastodon.rss.frontend.screen.account

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.matsudamper.mastodon.rss.frontend.format.UnixTimeUtil
import net.matsudamper.mastodon.rss.frontend.logic.account.AccountApi
import net.matsudamper.mastodon.rss.frontend.logic.account.AccountNote
import net.matsudamper.mastodon.rss.frontend.logic.account.AccountNotesResult
import net.matsudamper.mastodon.rss.frontend.logic.account.AccountResult

/**
 * @param username URL に入っていた名前。綴りが違っていても引けるので、
 *   画面に出すのは取ってきた方の名前にする
 * @param host 画面を開いているホスト。仮の値の組み立てにだけ使う
 */
class AccountScreenViewModel(
    private val username: String,
    private val host: String,
    private val viewModelScope: CoroutineScope,
    private val api: AccountApi = AccountApi(),
    private val copyToClipboard: (String, (Boolean) -> Unit) -> Unit,
    private val showSnackbar: (String) -> Unit,
) {
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState())

    private var loadingJob: Job? = null
    private var loadMoreJob: Job? = null

    val uiStateFlow: StateFlow<AccountScreenUiState> =
        MutableStateFlow(
            AccountScreenUiState(
                content = AccountScreenUiState.Content.Loading,
                listener =
                object : AccountScreenUiState.Listener {
                    override fun onClickReload() {
                        reload()
                    }

                    override fun onClickReloadNotes() {
                        reloadNotes()
                    }

                    override fun onClickLoadMore() {
                        loadMore()
                    }

                    override fun onClickCopyAcct() {
                        copyAcct()
                    }
                },
            ),
        ).also { uiStateFlow ->
            viewModelScope.launch {
                viewModelStateFlow.collect { viewModelState ->
                    uiStateFlow.update { uiState ->
                        uiState.copy(content = createContent(viewModelState))
                    }
                }
            }
        }.asStateFlow()

    fun onStart() {
        reload()
    }

    private fun reload() {
        loadingJob?.cancel()
        loadMoreJob?.cancel()
        viewModelStateFlow.update { ViewModelState() }

        loadingJob =
            viewModelScope.launch {
                when (val result = api.account(username = username, notesLimit = PAGE_SIZE)) {
                    is AccountResult.Success -> {
                        viewModelStateFlow.update {
                            it.copy(
                                account = result,
                                notes = result.notes,
                                notesCursor = result.notesCursor,
                                notesError = null,
                                notesLoading = false,
                            )
                        }
                    }

                    AccountResult.NotFound -> {
                        viewModelStateFlow.update { it.copy(account = AccountResult.NotFound) }
                    }

                    is AccountResult.Failure -> {
                        viewModelStateFlow.update { it.copy(account = result) }
                    }
                }
            }
    }

    private fun reloadNotes() {
        loadMoreJob?.cancel()
        viewModelStateFlow.update { it.copy(notesLoading = true, notesError = null) }

        loadingJob?.cancel()
        loadingJob =
            viewModelScope.launch {
                try {
                    when (val result = api.account(username = username, notesLimit = PAGE_SIZE)) {
                        is AccountResult.Success -> {
                            viewModelStateFlow.update {
                                it.copy(
                                    notes = result.notes,
                                    notesCursor = result.notesCursor,
                                    notesError = null,
                                    notesLoading = false,
                                    loadingMore = false,
                                )
                            }
                        }

                        is AccountResult.Failure -> {
                            viewModelStateFlow.update {
                                it.copy(
                                    notesError = result.message,
                                    notesLoading = false,
                                    loadingMore = false,
                                )
                            }
                        }

                        AccountResult.NotFound -> {
                            viewModelStateFlow.update {
                                it.copy(
                                    notesLoading = false,
                                    loadingMore = false,
                                )
                            }
                        }
                    }
                } finally {
                    if (!isActive) {
                        viewModelStateFlow.update { it.copy(notesLoading = false, loadingMore = false) }
                    }
                }
            }
    }

    private fun loadMore() {
        val cursor = viewModelStateFlow.value.notesCursor ?: return
        if (viewModelStateFlow.value.loadingMore) return

        loadMoreJob?.cancel()
        viewModelStateFlow.update { it.copy(loadingMore = true) }

        loadMoreJob =
            viewModelScope.launch {
                try {
                    when (val result = api.notes(username = username, cursor = cursor, limit = PAGE_SIZE)) {
                        is AccountNotesResult.Success -> {
                            viewModelStateFlow.update { current ->
                                current.copy(
                                    notes = current.notes + result.notes,
                                    notesCursor = result.cursor,
                                    notesError = null,
                                    loadingMore = false,
                                )
                            }
                        }

                        is AccountNotesResult.Failure -> {
                            viewModelStateFlow.update {
                                it.copy(
                                    notesError = result.message,
                                    loadingMore = false,
                                )
                            }
                        }
                    }
                } finally {
                    if (!isActive) {
                        viewModelStateFlow.update { it.copy(loadingMore = false) }
                    }
                }
            }
    }

    private fun copyAcct() {
        val acct =
            when (val account = viewModelStateFlow.value.account) {
                is AccountResult.Success -> account.account.acct
                else -> return
            }
        copyToClipboard(acct) { copied ->
            if (copied) {
                showSnackbar("コピーしました")
            }
        }
    }

    private fun createContent(state: ViewModelState): AccountScreenUiState.Content {
        return when (val account = state.account) {
            null -> AccountScreenUiState.Content.Loading

            AccountResult.NotFound -> AccountScreenUiState.Content.NotFound

            is AccountResult.Failure -> AccountScreenUiState.Content.Error(account.message)

            is AccountResult.Success -> {
                AccountScreenUiState.Content.Loaded(
                    account = AccountUiState.placeholder(
                        username = account.account.username,
                        acct = account.account.acct,
                        actorUrl = account.account.actorUrl,
                        host = host,
                    ),
                    notes = state.notes.map { it.toUiState() },
                    notesError = state.notesError,
                    notesLoading = state.notesLoading,
                    canLoadMore = state.notesCursor != null,
                    loadingMore = state.loadingMore,
                )
            }
        }
    }

    private fun AccountNote.toUiState(): NoteUiState = NoteUiState(
        url = url,
        contentHtml = contentHtml,
        publishedAt = UnixTimeUtil.format(publishedAt.epochSeconds),
    )

    private data class ViewModelState(
        val account: AccountResult? = null,
        val notes: List<AccountNote> = emptyList(),
        val notesError: String? = null,
        val notesLoading: Boolean = false,
        val notesCursor: String? = null,
        val loadingMore: Boolean = false,
    )

    private companion object {
        const val PAGE_SIZE: Int = 20
    }
}
