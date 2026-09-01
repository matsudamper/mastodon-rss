package net.matsudamper.mastodon.rss.frontend.screen.admin

import kotlinx.coroutines.CoroutineScope
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
import net.matsudamper.mastodon.rss.frontend.navigation.NavigatorReceiver
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.navigation.ScreenNavigator

class AdminAccountsScreenViewModel(
    private val viewModelScope: CoroutineScope,
    navigationEvents: EventSender<NavigatorReceiver>,
    private val api: AdminApi = AdminApi(),
) {
    private val navigator = ScreenNavigator(navigationEvents, viewModelScope)
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState())

    val uiStateFlow: StateFlow<AdminAccountsScreenUiState> =
        MutableStateFlow(
            AdminAccountsScreenUiState(
                content = AdminAccountsScreenUiState.Content.Loading,
                listener = object : AdminAccountsScreenUiState.Listener {
                    override fun onClickHome() {
                        navigator.navigate(Screen.Home)
                    }

                    override fun onClickAdmin() {
                        navigator.navigate(Screen.Admin)
                    }

                    override fun onClickNewAccount() {
                        navigator.navigate(Screen.AdminAccountNew)
                    }

                    override fun onClickPublicAccount(username: String) {
                        navigator.navigate(Screen.Account(username))
                    }

                    override fun onClickAdminAccount(username: String) {
                        navigator.navigate(Screen.AdminAccount(username))
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

    private fun reload() {
        viewModelStateFlow.update { ViewModelState() }
        viewModelScope.launch {
            val session = api.session()
            viewModelStateFlow.update { it.copy(session = session) }

            if (session is AdminSessionResult.Success && session.loggedIn) {
                viewModelStateFlow.update { it.copy(accounts = api.accounts()) }
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
}
