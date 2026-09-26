package net.matsudamper.mastodon.rss.frontend.screen.admin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.mastodon.rss.frontend.event.EventSender
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminApi
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminBroadcastActorUpdatesResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminLoginResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminSessionResult
import net.matsudamper.mastodon.rss.frontend.navigation.Screen

internal class AdminScreenViewModel(
    private val viewModelScope: CoroutineScope,
    private val api: AdminApi = AdminApi(),
) {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState())
    private var sessionJob: Job? = null

    private val loggedInListener =
        object : AdminScreenUiState.Content.LoggedIn.Listener {
            override fun onClickLogout() {
                logout()
            }
        }

    private val actorUpdateBroadcastListener =
        object : AdminScreenUiState.ActorUpdateBroadcast.Listener {
            override fun onClickBroadcast() {
                viewModelStateFlow.update { it.copy(broadcastConfirmVisible = true) }
            }

            override fun onClickConfirm() {
                broadcastActorUpdates()
            }

            override fun onDismissConfirm() {
                viewModelStateFlow.update { it.copy(broadcastConfirmVisible = false) }
            }
        }

    private val menuSections: List<AdminScreenUiState.MenuSection> =
        listOf(
            AdminScreenUiState.MenuSection(
                title = "アカウント",
                items = listOf(
                    navigationMenuItem(
                        title = "アカウントの一覧",
                        description = "登録したアカウントを見る。投稿とフォロワーは一覧から選んだ先にある。",
                        screen = Screen.AdminAccounts,
                    ),
                    navigationMenuItem(
                        title = "アカウントの追加",
                        description = "フィードを流すアカウントを新しく作る。",
                        screen = Screen.AdminAccountNew,
                    ),
                ),
            ),
            AdminScreenUiState.MenuSection(
                title = "配信",
                items = listOf(
                    navigationMenuItem(
                        title = "送り直しを待っている配信",
                        description = "フォロワーの inbox に届かず、送り直しを待っている投稿を見る。",
                        screen = Screen.AdminDeliveries,
                    ),
                ),
            ),
        )

    val uiStateFlow: StateFlow<AdminScreenUiState> =
        MutableStateFlow(
            AdminScreenUiState(
                content = AdminScreenUiState.Content.Loading,
                listener =
                object : AdminScreenUiState.Listener {
                    override fun onClickHome() {
                        navigate(Screen.Home)
                    }

                    override fun onClickAdmin() {
                        navigate(Screen.Admin)
                    }

                    override fun onPasswordChanged(text: String) {
                        viewModelStateFlow.update { it.copy(password = text, error = null) }
                    }

                    override fun onClickLogin() {
                        login()
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

    private fun navigate(screen: Screen) {
        viewModelScope.launch {
            events.send { it.navigate(screen) }
        }
    }

    private fun reload() {
        sessionJob?.cancel()
        viewModelStateFlow.update { it.copy(session = null) }
        sessionJob = viewModelScope.launch {
            api.session().collect { session ->
                viewModelStateFlow.update { it.copy(session = session) }
            }
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
                        )
                    }
                    reload()
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

    private fun broadcastActorUpdates() {
        if (viewModelStateFlow.value.broadcasting) return

        viewModelStateFlow.update { it.copy(broadcastConfirmVisible = false, broadcasting = true, broadcastResult = null) }
        viewModelScope.launch {
            val result = api.broadcastActorUpdates()
            viewModelStateFlow.update { it.copy(broadcasting = false, broadcastResult = result) }
        }
    }

    private fun createActorUpdateBroadcast(state: ViewModelState): AdminScreenUiState.ActorUpdateBroadcast {
        val result = state.broadcastResult
        return AdminScreenUiState.ActorUpdateBroadcast(
            buttonLabel = if (state.broadcasting) "配り直し中..." else "全アカウントの情報を配り直す",
            buttonEnabled = !state.broadcasting,
            resultMessage = when (result) {
                null -> null

                is AdminBroadcastActorUpdatesResult.Success -> buildString {
                    append("${result.accountCount} アカウント分、${result.deliveryCount} 件の配信を積んだ。")
                    if (result.failedAccountCount > 0) {
                        append("${result.failedAccountCount} アカウントは積めなかった。")
                    }
                }

                is AdminBroadcastActorUpdatesResult.Failure -> result.message
            },
            resultIsError = result is AdminBroadcastActorUpdatesResult.Failure ||
                (result is AdminBroadcastActorUpdatesResult.Success && result.failedAccountCount > 0),
            confirmDialogVisible = state.broadcastConfirmVisible,
            listener = actorUpdateBroadcastListener,
        )
    }

    private fun navigationMenuItem(
        title: String,
        description: String,
        screen: Screen,
    ): AdminScreenUiState.MenuItem {
        return AdminScreenUiState.MenuItem(
            title = title,
            description = description,
            listener = object : AdminScreenUiState.MenuItem.Listener {
                override fun onClick() {
                    navigate(screen)
                }
            },
        )
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
                            sections = menuSections,
                            actorUpdateBroadcast = createActorUpdateBroadcast(state),
                            listener = loggedInListener,
                        )
                    }

                    else -> {
                        val input =
                            if (session.passwordConfigured) {
                                AdminScreenUiState.Content.Login.Input.Enabled
                            } else {
                                AdminScreenUiState.Content.Login.Input.Disabled(LOGIN_DISABLED_MESSAGE)
                            }
                        val passwordInputEnabled = input is AdminScreenUiState.Content.Login.Input.Enabled && !state.submitting
                        AdminScreenUiState.Content.Login(
                            password = state.password,
                            submitting = state.submitting,
                            error = state.error,
                            input = input,
                            passwordInputEnabled = passwordInputEnabled,
                            loginButtonEnabled = passwordInputEnabled && state.password.isNotEmpty(),
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
        val broadcastConfirmVisible: Boolean = false,
        val broadcasting: Boolean = false,
        val broadcastResult: AdminBroadcastActorUpdatesResult? = null,
    )

    interface Event {
        suspend fun navigate(screen: Screen)
    }

    private companion object {
        const val LOGIN_DISABLED_MESSAGE = "ログインが無効化されている"
    }
}
