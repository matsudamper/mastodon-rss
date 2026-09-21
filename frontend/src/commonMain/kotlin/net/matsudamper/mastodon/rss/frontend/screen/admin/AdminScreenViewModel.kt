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
            AdminScreenUiState.MenuSection(
                title = "フィード",
                items = listOf(
                    AdminScreenUiState.MenuItem(
                        title = "フィードの登録と再取得",
                        description = "フィードの登録・削除と、手動での再取得。",
                        availability = AdminScreenUiState.MenuItem.Availability.Planned(note = "これから作る。"),
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

    private fun navigationMenuItem(
        title: String,
        description: String,
        screen: Screen,
    ): AdminScreenUiState.MenuItem {
        return AdminScreenUiState.MenuItem(
            title = title,
            description = description,
            availability = AdminScreenUiState.MenuItem.Availability.Available(
                listener = object : AdminScreenUiState.MenuItem.Listener {
                    override fun onClick() {
                        navigate(screen)
                    }
                },
            ),
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
    )

    interface Event {
        suspend fun navigate(screen: Screen)
    }

    private companion object {
        const val LOGIN_DISABLED_MESSAGE = "ログインが無効化されている"
    }
}
