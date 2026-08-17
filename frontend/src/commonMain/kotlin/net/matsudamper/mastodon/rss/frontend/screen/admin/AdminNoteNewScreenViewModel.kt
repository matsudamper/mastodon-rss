package net.matsudamper.mastodon.rss.frontend.screen.admin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminAccountsResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminApi
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminNote
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminNotesResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminPostNoteResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminSessionResult

class AdminNoteNewScreenViewModel(
    private val viewModelScope: CoroutineScope,
    private val api: AdminApi = AdminApi(),
) {
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState())

    val uiStateFlow: StateFlow<AdminNoteNewScreenUiState> =
        MutableStateFlow(
            AdminNoteNewScreenUiState(
                content = AdminNoteNewScreenUiState.Content.Loading,
                listener = object : AdminNoteNewScreenUiState.Listener {
                    override fun onBodyChanged(text: String) {
                        viewModelStateFlow.update { it.copy(body = text, error = null, result = null) }
                    }

                    override fun onAccountSelected(username: String) {
                        // 残したまま取りに行くと、届くまで別のアカウントの投稿が出たままになる
                        viewModelStateFlow.update {
                            it.copy(
                                selectedUsername = username,
                                result = null,
                                notes = emptyList(),
                                cursor = null,
                                error = null,
                            )
                        }
                        loadNotes(username)
                    }

                    override fun onClickPost() {
                        post()
                    }

                    override fun onClickLoadMore() {
                        loadMore()
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
        viewModelScope.launch {
            val session = api.session()
            viewModelStateFlow.update { it.copy(session = session) }

            if (session !is AdminSessionResult.Success || !session.loggedIn) return@launch

            when (val accounts = api.accounts()) {
                is AdminAccountsResult.Success -> {
                    val selected = accounts.accounts.firstOrNull()?.username.orEmpty()
                    viewModelStateFlow.update {
                        it.copy(
                            accounts = accounts.accounts.map { account ->
                                AdminNoteNewScreenUiState.Account(
                                    username = account.username,
                                    acct = account.acct,
                                )
                            },
                            selectedUsername = selected,
                        )
                    }
                    if (selected.isNotEmpty()) loadNotes(selected)
                }

                is AdminAccountsResult.Failure -> {
                    viewModelStateFlow.update { it.copy(error = accounts.message) }
                }
            }
        }
    }

    /**
     * 先頭から取り直す。
     *
     * 取得のたびに世代を上げ、最新の取得の結果だけを反映する。名前だけで見ると、
     * 同じアカウントで取り直したときに古い応答が後から届いて上書きしてしまう。
     */
    private fun loadNotes(username: String) {
        val generation = viewModelStateFlow.updateAndGet {
            it.copy(loadGeneration = it.loadGeneration + 1)
        }.loadGeneration

        viewModelScope.launch {
            when (val result = api.notes(username = username, limit = PAGE_SIZE)) {
                is AdminNotesResult.Success -> {
                    if (viewModelStateFlow.value.loadGeneration != generation) return@launch
                    viewModelStateFlow.update {
                        it.copy(notes = result.notes, cursor = result.cursor, loadingMore = false)
                    }
                }

                is AdminNotesResult.Failure -> {
                    if (viewModelStateFlow.value.loadGeneration != generation) return@launch
                    viewModelStateFlow.update { it.copy(error = result.message, loadingMore = false) }
                }
            }
        }
    }

    /**
     * 続きを足す。cursor が無ければ最後まで出ている
     */
    private fun loadMore() {
        val state = viewModelStateFlow.value
        val cursor = state.cursor ?: return
        if (state.loadingMore) return

        val username = state.selectedUsername
        val generation = state.loadGeneration
        viewModelStateFlow.update { it.copy(loadingMore = true) }

        viewModelScope.launch {
            when (val result = api.notes(username = username, cursor = cursor, limit = PAGE_SIZE)) {
                is AdminNotesResult.Success -> {
                    if (viewModelStateFlow.value.loadGeneration != generation) return@launch
                    viewModelStateFlow.update {
                        it.copy(notes = it.notes + result.notes, cursor = result.cursor, loadingMore = false)
                    }
                }

                is AdminNotesResult.Failure -> {
                    if (viewModelStateFlow.value.loadGeneration != generation) return@launch
                    viewModelStateFlow.update { it.copy(error = result.message, loadingMore = false) }
                }
            }
        }
    }

    private fun post() {
        val state = viewModelStateFlow.value
        if (state.submitting || state.body.isBlank() || state.selectedUsername.isEmpty()) return

        val username = state.selectedUsername
        viewModelStateFlow.update { it.copy(submitting = true, error = null, result = null) }

        viewModelScope.launch {
            when (val result = api.postNote(username = username, body = state.body.trim())) {
                is AdminPostNoteResult.Success -> {
                    viewModelStateFlow.update {
                        it.copy(
                            submitting = false,
                            body = "",
                            result = AdminNoteNewScreenUiState.PostResult(
                                url = result.note.url,
                                targets = result.deliveryTargets,
                                delivered = result.delivered,
                            ),
                        )
                    }
                    loadNotes(username)
                }

                is AdminPostNoteResult.Rejected -> {
                    viewModelStateFlow.update { it.copy(submitting = false, error = rejectedMessage(result)) }
                }

                is AdminPostNoteResult.Failure -> {
                    viewModelStateFlow.update { it.copy(submitting = false, error = result.message) }
                }
            }
        }
    }

    private fun rejectedMessage(rejected: AdminPostNoteResult.Rejected): String = buildList {
        if (rejected.unknownAccount) add("そのアカウントは応答しない")
        if (rejected.isEmpty) add("本文が空")
        if (rejected.maxLength != null) add("${rejected.maxLength} 文字までにする")
    }.joinToString("\n").ifEmpty { "投稿できなかった" }

    private fun createContent(state: ViewModelState): AdminNoteNewScreenUiState.Content {
        val session = state.session ?: return AdminNoteNewScreenUiState.Content.Loading

        when (session) {
            is AdminSessionResult.Failure -> return AdminNoteNewScreenUiState.Content.Error(session.message)

            is AdminSessionResult.Success -> {
                if (!session.loggedIn) return AdminNoteNewScreenUiState.Content.RequireLogin
            }
        }

        return AdminNoteNewScreenUiState.Content.Input(
            accounts = state.accounts,
            selectedUsername = state.selectedUsername,
            body = state.body,
            submitting = state.submitting,
            result = state.result,
            error = state.error,
            notes = state.notes.map { note ->
                AdminNoteNewScreenUiState.Note(url = note.url, text = note.contentHtml.toPlainText())
            },
            canLoadMore = state.cursor != null,
            loadingMore = state.loadingMore,
        )
    }

    /**
     * 配信した HTML を画面に出す形に直す。段落と改行だけを改行に戻す
     */
    private fun String.toPlainText(): String = replace("</p>", "\n")
        .replace("<br>", "\n")
        .replace(Regex("<[^>]*>"), "")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&amp;", "&")
        .trim()

    private data class ViewModelState(
        val session: AdminSessionResult? = null,
        val accounts: List<AdminNoteNewScreenUiState.Account> = emptyList(),
        val selectedUsername: String = "",
        val body: String = "",
        val submitting: Boolean = false,
        val result: AdminNoteNewScreenUiState.PostResult? = null,
        val error: String? = null,
        val notes: List<AdminNote> = emptyList(),
        val cursor: String? = null,
        val loadingMore: Boolean = false,
        /**
         * 一覧の取得の世代。取り直すたびに上がる
         */
        val loadGeneration: Int = 0,
    )

    private companion object {
        /**
         * 1 回に取る件数。上限はサーバー側で決まる
         */
        const val PAGE_SIZE = 20
    }
}
