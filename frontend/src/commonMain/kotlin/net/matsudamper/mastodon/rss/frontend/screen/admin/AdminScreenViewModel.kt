package net.matsudamper.mastodon.rss.frontend.screen.admin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.mastodon.rss.frontend.api.AdminAccountsResult
import net.matsudamper.mastodon.rss.frontend.api.AdminAddAccountResult
import net.matsudamper.mastodon.rss.frontend.api.AdminApi
import net.matsudamper.mastodon.rss.frontend.api.AdminLoginResult
import net.matsudamper.mastodon.rss.frontend.api.AdminSessionResult

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

                    override fun onAddAccountUsernameChanged(text: String) {
                        viewModelStateFlow.update {
                            it.copy(addAccountUsername = text, addAccountError = null)
                        }
                    }

                    override fun onClickAddAccount() {
                        addAccount()
                    }

                    override fun onClickReloadAccounts() {
                        loadAccounts()
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

            if (session is AdminSessionResult.Success && session.loggedIn) loadAccounts()
        }
    }

    private fun loadAccounts() {
        viewModelStateFlow.update { it.copy(accounts = null) }
        viewModelScope.launch {
            val accounts = api.accounts()
            viewModelStateFlow.update { it.copy(accounts = accounts) }
        }
    }

    private fun login() {
        val state = viewModelStateFlow.value
        if (state.submitting || state.password.isEmpty()) return

        viewModelStateFlow.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (val result = api.login(state.password)) {
                AdminLoginResult.Success -> {
                    viewModelStateFlow.update {
                        it.copy(
                            submitting = false,
                            session = AdminSessionResult.Success(loggedIn = true, passwordConfigured = true),
                        )
                    }
                    loadAccounts()
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
            viewModelStateFlow.update {
                it.copy(
                    session = session,
                    password = "",
                    error = null,
                    // ログアウトした後の画面に、前に見えていた一覧が残らないようにする
                    accounts = null,
                    addAccountUsername = "",
                    addAccountError = null,
                )
            }
        }
    }

    private fun addAccount() {
        val state = viewModelStateFlow.value
        if (state.addAccountSubmitting || state.addAccountUsername.isBlank()) return

        viewModelStateFlow.update { it.copy(addAccountSubmitting = true, addAccountError = null) }
        viewModelScope.launch {
            when (val result = api.addAccount(state.addAccountUsername.trim())) {
                is AdminAddAccountResult.Success -> {
                    viewModelStateFlow.update {
                        it.copy(addAccountSubmitting = false, addAccountUsername = "")
                    }
                    loadAccounts()
                }

                AdminAddAccountResult.InvalidUsername -> {
                    addAccountFailed("英数字と _ . - のみ、先頭と末尾は英数字か _ にする")
                }

                AdminAddAccountResult.ReservedUsername -> {
                    addAccountFailed("サーバーの設定で決まっているアカウントと同じ名前は使えない")
                }

                AdminAddAccountResult.Duplicated -> {
                    addAccountFailed("同じ名前のアカウントが既にある")
                }

                is AdminAddAccountResult.Failure -> {
                    addAccountFailed(result.message)
                }
            }
        }
    }

    private fun addAccountFailed(message: String) {
        viewModelStateFlow.update { it.copy(addAccountSubmitting = false, addAccountError = message) }
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
                        AdminScreenUiState.Content.LoggedIn(
                            accounts = createAccounts(state.accounts),
                            addAccount =
                            AdminScreenUiState.AddAccount(
                                username = state.addAccountUsername,
                                submitting = state.addAccountSubmitting,
                                error = state.addAccountError,
                            ),
                        )
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

    private fun createAccounts(result: AdminAccountsResult?): AdminScreenUiState.Accounts {
        return when (result) {
            null -> AdminScreenUiState.Accounts.Loading

            is AdminAccountsResult.Failure -> AdminScreenUiState.Accounts.Error(result.message)

            is AdminAccountsResult.Success -> {
                AdminScreenUiState.Accounts.Loaded(
                    items =
                    result.accounts.map { account ->
                        AdminScreenUiState.Account(
                            username = account.username,
                            acct = account.acct,
                            actorUrl = account.actorUrl,
                            fromConfigLabel = if (account.fromConfig) FROM_CONFIG_LABEL else null,
                            createdAt = account.createdAt,
                        )
                    },
                )
            }
        }
    }

    private data class ViewModelState(
        val session: AdminSessionResult? = null,
        val password: String = "",
        val submitting: Boolean = false,
        val error: String? = null,
        val accounts: AdminAccountsResult? = null,
        val addAccountUsername: String = "",
        val addAccountSubmitting: Boolean = false,
        val addAccountError: String? = null,
    )

    private companion object {
        const val LOGIN_DISABLED_MESSAGE = "ログインが無効化されている"
        const val FROM_CONFIG_LABEL = "設定"
    }
}
