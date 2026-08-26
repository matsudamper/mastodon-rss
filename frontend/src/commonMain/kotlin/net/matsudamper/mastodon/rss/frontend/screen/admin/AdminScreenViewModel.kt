package net.matsudamper.mastodon.rss.frontend.screen.admin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminApi
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminLoginResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminSessionResult

class AdminScreenViewModel(
    private val viewModelScope: CoroutineScope,
    private val api: AdminApi = AdminApi(),
) {
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState())

    val uiStateFlow: StateFlow<AdminScreenUiState> =
        MutableStateFlow(
            AdminScreenUiState(
                content = AdminScreenUiState.Content.Loading,
                listener =
                object : AdminScreenUiState.Listener {
                    override fun onPasswordChanged(text: String) {
                        viewModelStateFlow.update { it.copy(password = text, error = null) }
                    }

                    override fun onClickLogin() {
                        login()
                    }

                    override fun onClickLogout() {
                        logout()
                    }

                    override fun onClickRetry() {
                        reload()
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
        viewModelStateFlow.update { it.copy(session = null) }
        viewModelScope.launch {
            val session = api.session()
            viewModelStateFlow.update { it.copy(session = session) }
        }
    }

    private fun login() {
        val state = viewModelStateFlow.value
        if (state.submitting || state.password.isEmpty()) return

        viewModelStateFlow.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (val result = api.login(state.password)) {
                AdminLoginResult.Success -> {
                    val session = api.session()
                    viewModelStateFlow.update {
                        it.copy(
                            submitting = false,
                            session = session,
                        )
                    }
                }

                AdminLoginResult.WrongPassword -> {
                    viewModelStateFlow.update { it.copy(submitting = false, error = "パスワードが違う") }
                }

                AdminLoginResult.NotConfigured -> {
                    viewModelStateFlow.update {
                        it.copy(
                            submitting = false,
                            session = AdminSessionResult.Success(loggedIn = false, passwordConfigured = false),
                        )
                    }
                }

                is AdminLoginResult.Failure -> {
                    viewModelStateFlow.update { it.copy(submitting = false, error = result.message) }
                }
            }
        }
    }

    private fun logout() {
        viewModelStateFlow.update { it.copy(session = null) }
        viewModelScope.launch {
            val session = api.logout()
            viewModelStateFlow.update { it.copy(session = session, password = "", error = null) }
        }
    }

    private fun createContent(state: ViewModelState): AdminScreenUiState.Content {
        return when (val session = state.session) {
            null -> {
                AdminScreenUiState.Content.Loading
            }

            is AdminSessionResult.Failure -> {
                AdminScreenUiState.Content.Error(session.message)
            }

            is AdminSessionResult.Success -> {
                when {
                    session.loggedIn -> {
                        AdminScreenUiState.Content.LoggedIn
                    }

                    else -> {
                        AdminScreenUiState.Content.Login(
                            password = state.password,
                            submitting = state.submitting,
                            error = state.error,
                            input =
                            if (session.passwordConfigured) {
                                AdminScreenUiState.Content.Login.Input.Enabled
                            } else {
                                AdminScreenUiState.Content.Login.Input.Disabled(LOGIN_DISABLED_MESSAGE)
                            },
                        )
                    }
                }
            }
        }
    }

    private data class ViewModelState(
        val session: AdminSessionResult? = null,
        val password: String = "",
        val submitting: Boolean = false,
        val error: String? = null,
    )

    private companion object {
        const val LOGIN_DISABLED_MESSAGE = "ログインが無効化されている"
    }
}
