package net.matsudamper.mastodon.rss.frontend.screen.admin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.matsudamper.mastodon.rss.frontend.format.UnixTimeUtil
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminAccount
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminAccountResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminApi
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminNote
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminNotesResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminPostNoteResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminSessionResult

class AdminAccountScreenViewModel(
    private val username: String,
    private val viewModelScope: CoroutineScope,
    private val api: AdminApi = AdminApi(),
) {
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState())

    private var reloadJob: Job? = null
    private var notesJob: Job? = null
    private var loadMoreJob: Job? = null
    private var postJob: Job? = null

    val uiStateFlow: StateFlow<AdminAccountScreenUiState> =
        MutableStateFlow(
            AdminAccountScreenUiState(
                acct = "@$username",
                content = AdminAccountScreenUiState.Content.Loading,
                listener = object : AdminAccountScreenUiState.Listener {
                    override fun onBodyChanged(text: String) {
                        viewModelStateFlow.update { it.copy(body = text, error = null, result = null) }
                    }

                    override fun onClickPost() {
                        post()
                    }

                    override fun onClickLoadMore() {
                        loadMore()
                    }

                    override fun onClickReloadNotes() {
                        loadNotes()
                    }

                    override fun onClickReload() {
                        reload()
                    }
                },
            ),
        ).also { uiStateFlow ->
            viewModelScope.launch {
                viewModelStateFlow.collect { viewModelState ->
                    uiStateFlow.update { uiState -> uiState.copy(content = createContent(viewModelState)) }
                }
            }
        }.asStateFlow()

    fun onStart() {
        reload()
    }

    private fun reload() {
        reloadJob?.cancel()
        postJob?.cancel()
        cancelNotesJobs()

        viewModelStateFlow.update { ViewModelState(body = it.body) }

        reloadJob = viewModelScope.launch {
            val session = api.session()
            viewModelStateFlow.update { it.copy(session = session) }

            if (session !is AdminSessionResult.Success || !session.loggedIn) return@launch

            val account = api.account(username)
            viewModelStateFlow.update { it.copy(account = account) }

            if (account is AdminAccountResult.Success && account.account != null) {
                loadNotes()
            }
        }
    }

    /**
     * 投稿の一覧を先頭から取り直す。
     */
    private fun loadNotes() {
        cancelNotesJobs()
        viewModelStateFlow.update { it.copy(notesLoading = true, notesError = null) }

        notesJob = viewModelScope.launch {
            try {
                when (val result = api.notes(username = username, limit = PAGE_SIZE)) {
                    is AdminNotesResult.Success -> {
                        viewModelStateFlow.update {
                            it.copy(
                                notes = result.notes,
                                notesError = null,
                                cursor = result.cursor,
                                loadingMore = false,
                                notesLoading = false,
                            )
                        }
                    }

                    is AdminNotesResult.Failure -> {
                        viewModelStateFlow.update {
                            it.copy(
                                notesError = result.message,
                                loadingMore = false,
                                notesLoading = false,
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
        val cursor = viewModelStateFlow.value.cursor ?: return
        if (viewModelStateFlow.value.loadingMore) return

        loadMoreJob?.cancel()
        viewModelStateFlow.update { it.copy(loadingMore = true) }

        loadMoreJob = viewModelScope.launch {
            try {
                when (val result = api.notes(username = username, cursor = cursor, limit = PAGE_SIZE)) {
                    is AdminNotesResult.Success -> {
                        viewModelStateFlow.update { current ->
                            current.copy(
                                notes = current.notes + result.notes,
                                notesError = null,
                                cursor = result.cursor,
                                loadingMore = false,
                            )
                        }
                    }

                    is AdminNotesResult.Failure -> {
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

    private fun cancelNotesJobs() {
        notesJob?.cancel()
        loadMoreJob?.cancel()
        notesJob = null
        loadMoreJob = null
    }

    private fun post() {
        val state = viewModelStateFlow.value
        val body = state.body.trim()
        if (body.isEmpty() || state.submitting) return

        postJob?.cancel()
        viewModelStateFlow.update { it.copy(submitting = true, error = null, result = null) }

        postJob = viewModelScope.launch {
            try {
                when (val result = api.postNote(username = username, body = body)) {
                    is AdminPostNoteResult.Success -> {
                        viewModelStateFlow.update {
                            it.copy(
                                body = "",
                                submitting = false,
                                result = AdminAccountScreenUiState.PostResult(
                                    url = result.note.url,
                                    targets = result.deliveryTargets,
                                    delivered = result.delivered,
                                ),
                                error = null,
                            )
                        }
                        loadNotes()
                    }

                    is AdminPostNoteResult.Rejected -> {
                        viewModelStateFlow.update {
                            it.copy(
                                submitting = false,
                                error = rejectedMessage(result),
                            )
                        }
                    }

                    is AdminPostNoteResult.Failure -> {
                        viewModelStateFlow.update { it.copy(submitting = false, error = result.message) }
                    }
                }
            } finally {
                if (!isActive) {
                    viewModelStateFlow.update { it.copy(submitting = false) }
                }
            }
        }
    }

    private fun rejectedMessage(rejected: AdminPostNoteResult.Rejected): String = buildList {
        if (rejected.unknownAccount) add("このアカウントは応答しない")
        if (rejected.isEmpty) add("本文が空")
        if (rejected.maxLength != null) add("${rejected.maxLength} 文字までにする")
    }.joinToString("\n").ifEmpty { "投稿できなかった" }

    private fun createContent(state: ViewModelState): AdminAccountScreenUiState.Content {
        val session = state.session ?: return AdminAccountScreenUiState.Content.Loading

        when (session) {
            is AdminSessionResult.Failure -> return AdminAccountScreenUiState.Content.Error(session.message)

            is AdminSessionResult.Success -> {
                if (!session.loggedIn) return AdminAccountScreenUiState.Content.RequireLogin
            }
        }

        return when (val account = state.account) {
            null -> AdminAccountScreenUiState.Content.Loading

            is AdminAccountResult.Failure -> AdminAccountScreenUiState.Content.Error(account.message)

            is AdminAccountResult.Success -> {
                val found = account.account ?: return AdminAccountScreenUiState.Content.NotFound

                AdminAccountScreenUiState.Content.Loaded(
                    account = found.toUiState(),
                    post = AdminAccountScreenUiState.Post(
                        body = state.body,
                        submitting = state.submitting,
                        result = state.result,
                        error = state.error,
                    ),
                    notes = state.notes.map { it.toUiState() },
                    notesError = state.notesError,
                    notesLoading = state.notesLoading,
                    canLoadMore = state.cursor != null,
                    loadingMore = state.loadingMore,
                )
            }
        }
    }

    private fun AdminAccount.toUiState(): AdminAccountScreenUiState.Account = AdminAccountScreenUiState.Account(
        username = account.username,
        acct = account.acct,
        actorUrl = account.actorUrl,
        createdAt = createdAt?.let { UnixTimeUtil.format(it) },
        followerCount = followerCount,
    )

    private fun AdminNote.toUiState(): AdminAccountScreenUiState.Note = AdminAccountScreenUiState.Note(
        url = url,
        contentHtml = contentHtml,
        publishedAt = UnixTimeUtil.format(publishedAt.epochSeconds),
    )

    private data class ViewModelState(
        val session: AdminSessionResult? = null,
        val account: AdminAccountResult? = null,
        val body: String = "",
        val submitting: Boolean = false,
        val result: AdminAccountScreenUiState.PostResult? = null,
        val error: String? = null,
        val notes: List<AdminNote> = emptyList(),
        val notesError: String? = null,
        val notesLoading: Boolean = false,
        val cursor: String? = null,
        val loadingMore: Boolean = false,
    )

    private companion object {
        /**
         * 1 回に取る件数。上限はサーバー側で決まる
         */
        const val PAGE_SIZE = 20
    }
}
