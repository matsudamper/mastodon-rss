package net.matsudamper.mastodon.rss.frontend.screen.admin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.mastodon.rss.frontend.api.AdminAddAccountResult
import net.matsudamper.mastodon.rss.frontend.api.AdminApi
import net.matsudamper.mastodon.rss.frontend.api.AdminSessionResult

class AdminAccountNewScreenViewModel(
    private val viewModelScope: CoroutineScope,
    private val api: AdminApi = AdminApi(),
) {
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState())

    val uiStateFlow: StateFlow<AdminAccountNewScreenUiState> =
        MutableStateFlow(
            AdminAccountNewScreenUiState(
                content = AdminAccountNewScreenUiState.Content.Loading,
                listener =
                object : AdminAccountNewScreenUiState.Listener {
                    override fun onUsernameChanged(text: String) {
                        viewModelStateFlow.update { it.copy(username = text, error = null) }
                    }

                    override fun onClickAdd() {
                        add()
                    }

                    override fun onClickAddAnother() {
                        viewModelStateFlow.update { it.copy(added = null, username = "", error = null) }
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
        // ログインしていなければ追加は errors になる。フォームを出してから
        // 弾くより、開いた時点でログインへ送る方が短い
        viewModelScope.launch {
            val session = api.session()
            viewModelStateFlow.update { it.copy(session = session) }
        }
    }

    private fun add() {
        val state = viewModelStateFlow.value
        if (state.submitting || state.username.isBlank()) return

        viewModelStateFlow.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (val result = api.addAccount(state.username.trim())) {
                is AdminAddAccountResult.Success -> {
                    viewModelStateFlow.update {
                        it.copy(submitting = false, added = result.account.acct)
                    }
                }

                AdminAddAccountResult.InvalidUsername -> {
                    failed("英数字と _ . - のみ、先頭と末尾は英数字か _、30 文字までにする")
                }

                AdminAddAccountResult.Duplicated -> {
                    failed("同じ名前のアカウントが既にある")
                }

                is AdminAddAccountResult.Failure -> {
                    failed(result.message)
                }
            }
        }
    }

    private fun failed(message: String) {
        viewModelStateFlow.update { it.copy(submitting = false, error = message) }
    }

    private fun createContent(state: ViewModelState): AdminAccountNewScreenUiState.Content {
        val session = state.session ?: return AdminAccountNewScreenUiState.Content.Loading

        when (session) {
            is AdminSessionResult.Failure -> {
                return AdminAccountNewScreenUiState.Content.Error(session.message)
            }

            is AdminSessionResult.Success -> {
                if (!session.loggedIn) return AdminAccountNewScreenUiState.Content.RequireLogin
            }
        }

        if (state.added != null) return AdminAccountNewScreenUiState.Content.Added(state.added)

        return AdminAccountNewScreenUiState.Content.Input(
            username = state.username,
            submitting = state.submitting,
            error = state.error,
        )
    }

    private data class ViewModelState(
        val session: AdminSessionResult? = null,
        val username: String = "",
        val submitting: Boolean = false,
        val error: String? = null,
        val added: String? = null,
    )
}
