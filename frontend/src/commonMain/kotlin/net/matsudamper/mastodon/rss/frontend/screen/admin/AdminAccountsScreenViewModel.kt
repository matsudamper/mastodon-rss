package net.matsudamper.mastodon.rss.frontend.screen.admin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.mastodon.rss.frontend.event.EventSender
import net.matsudamper.mastodon.rss.frontend.format.UnixTimeUtil
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminAccountsResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminApi
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminSessionResult
import net.matsudamper.mastodon.rss.frontend.navigation.Screen

class AdminAccountsScreenViewModel(
    private val viewModelScope: CoroutineScope,
    private val api: AdminApi = AdminApi(),
) {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState())
    private var sessionJob: Job? = null
    private var accountsJob: Job? = null

    val uiStateFlow: StateFlow<AdminAccountsScreenUiState> =
        MutableStateFlow(
            AdminAccountsScreenUiState(
                content = AdminAccountsScreenUiState.Content.Loading,
                listener = object : AdminAccountsScreenUiState.Listener {
                    override fun onClickHome() {
                        navigate(Screen.Home)
                    }

                    override fun onClickAdmin() {
                        navigate(Screen.Admin)
                    }

                    override fun onClickNewAccount() {
                        navigate(Screen.AdminAccountNew)
                    }

                    override fun onClickPublic(username: String) {
                        navigate(Screen.Account(username))
                    }

                    override fun onClickAccount(username: String) {
                        navigate(Screen.AdminAccount(username))
                    }

                    override fun onClickReload() {
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
        accountsJob?.cancel()
        accountsJob = null
        viewModelStateFlow.update { ViewModelState() }
        sessionJob = viewModelScope.launch {
            api.session().collect { session ->
                viewModelStateFlow.update { it.copy(session = session) }

                if (session is AdminSessionResult.Success && session.loggedIn) {
                    if (accountsJob == null) {
                        accountsJob = viewModelScope.launch {
                            api.accounts().collect { accounts ->
                                viewModelStateFlow.update { it.copy(accounts = accounts) }
                            }
                        }
                    }
                } else {
                    accountsJob?.cancel()
                    accountsJob = null
                    viewModelStateFlow.update { it.copy(accounts = null) }
                }
            }
        }
    }

    private fun createContent(state: ViewModelState): AdminAccountsScreenUiState.Content {
        val session = state.session ?: return AdminAccountsScreenUiState.Content.Loading

        when (session) {
            is AdminSessionResult.Failure -> {
                return AdminAccountsScreenUiState.Content.Error(session.message)
            }

            is AdminSessionResult.Success -> {
                if (!session.loggedIn) return AdminAccountsScreenUiState.Content.RequireLogin
            }
        }

        return when (val accounts = state.accounts) {
            null -> AdminAccountsScreenUiState.Content.Loading

            is AdminAccountsResult.Failure -> AdminAccountsScreenUiState.Content.Error(accounts.message)

            is AdminAccountsResult.Success -> {
                AdminAccountsScreenUiState.Content.Loaded(
                    accounts = accounts.accounts.map { account ->
                        AdminAccountsScreenUiState.Account(
                            username = account.account.username,
                            acct = account.account.acct,
                            actorUrl = account.account.actorUrl,
                            createdAt = UnixTimeUtil.format(account.createdAt),
                            followerCount = account.followerCount,
                        )
                    },
                )
            }
        }
    }

    private data class ViewModelState(
        val session: AdminSessionResult? = null,
        val accounts: AdminAccountsResult? = null,
    )

    interface Event {
        suspend fun navigate(screen: Screen)
    }
}
