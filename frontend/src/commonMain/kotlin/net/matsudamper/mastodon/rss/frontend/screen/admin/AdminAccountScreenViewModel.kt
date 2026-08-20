package net.matsudamper.mastodon.rss.frontend.screen.admin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
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
        viewModelStateFlow.update { ViewModelState(body = it.body) }

        viewModelScope.launch {
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
     *
     * 取得のたびに世代を上げ、最新の取得の結果だけを反映する。投稿した直後に
     * 取り直すので、投稿前に始まった取得が後から届くと投稿が消えて見える。
     */
    private fun loadNotes() {
        val generation = viewModelStateFlow.updateAndGet {
            it.copy(loadGeneration = it.loadGeneration + 1)
        }.loadGeneration

        viewModelScope.launch {
            when (val result = api.notes(username = username, limit = PAGE_SIZE)) {
                is AdminNotesResult.Success -> {
                    if (viewModelStateFlow.value.loadGeneration != generation) return@launch
                    viewModelStateFlow.update {
                        it.copy(
                            notes = result.notes,
                            notesError = null,
                            cursor = result.cursor,
                            loadingMore = false,
                        )
                    }
                }

                is AdminNotesResult.Failure -> {
                    if (viewModelStateFlow.value.loadGeneration != generation) return@launch
                    viewModelStateFlow.update {
                        it.copy(
                            notesError = result.message,
                            loadingMore = false,
                        )
                    }
                }
            }
        }
    }

    private fun loadMore() {
        val state = viewModelStateFlow.value
        val cursor = state.cursor ?: return
        if (state.loadingMore) return

        val generation = state.loadGeneration
        viewModelStateFlow.update { it.copy(loadingMore = true) }

        viewModelScope.launch {
            when (val result = api.notes(username = username, cursor = cursor, limit = PAGE_SIZE)) {
                is AdminNotesResult.Success -> {
                    if (viewModelStateFlow.value.loadGeneration != generation) return@launch
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
                    if (viewModelStateFlow.value.loadGeneration != generation) return@launch
                    viewModelStateFlow.update {
                        it.copy(
                            notesError = result.message,
                            loadingMore = false,
                        )
                    }
                }
            }
        }
    }

    private fun post() {
        val state = viewModelStateFlow.value
        val body = state.body.trim()
        if (body.isEmpty() || state.submitting) return

        viewModelStateFlow.update { it.copy(submitting = true, error = null, result = null) }

        viewModelScope.launch {
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
        text = contentHtml.toPlainText(),
        publishedAt = UnixTimeUtil.format(publishedAt.epochSeconds),
    )

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
        val account: AdminAccountResult? = null,
        val body: String = "",
        val submitting: Boolean = false,
        val result: AdminAccountScreenUiState.PostResult? = null,
        val error: String? = null,
        val notes: List<AdminNote> = emptyList(),
        val notesError: String? = null,
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
