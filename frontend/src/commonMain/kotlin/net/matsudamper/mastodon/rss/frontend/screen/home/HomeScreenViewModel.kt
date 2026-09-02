package net.matsudamper.mastodon.rss.frontend.screen.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.mastodon.rss.frontend.event.EventSender
import net.matsudamper.mastodon.rss.frontend.logic.account.AccountApi
import net.matsudamper.mastodon.rss.frontend.logic.account.AccountsResult
import net.matsudamper.mastodon.rss.frontend.navigation.Navigator
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.navigation.ScreenNavigator

class HomeScreenViewModel(
    private val viewModelScope: CoroutineScope,
    private val api: AccountApi = AccountApi(),
) {
    private val navigationEvents = EventSender<Navigator>()
    internal val navigationHandler = navigationEvents.asHandler()
    private val navigator = ScreenNavigator(navigationEvents, viewModelScope)
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState())

    val uiStateFlow: StateFlow<HomeScreenUiState> =
        MutableStateFlow(
            HomeScreenUiState(
                content = HomeScreenUiState.Content.Loading,
                listener =
                object : HomeScreenUiState.Listener {
                    override fun onClickHome() {
                        navigator.navigate(Screen.Home)
                    }

                    override fun onClickAdmin() {
                        navigator.navigate(Screen.Admin)
                    }

                    override fun onClickReload() {
                        reload()
                    }

                    override fun onClickLoadMore() {
                        loadMore()
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
        val state = viewModelStateFlow.value
        if (state.accounts == null && !state.isLoading) {
            reload()
        }
    }

    private fun reload() {
        viewModelStateFlow.update { ViewModelState(isLoading = true) }
        viewModelScope.launch {
            val result = api.accounts(limit = PAGE_SIZE)
            viewModelStateFlow.update {
                it.copy(
                    isLoading = false,
                    accounts = result,
                )
            }
        }
    }

    private fun loadMore() {
        val currentState = viewModelStateFlow.value
        val currentAccounts = currentState.accounts as? AccountsResult.Success ?: return
        if (!currentAccounts.hasMore || currentState.isLoadingMore) return

        val cursor = currentAccounts.nextCursor ?: return
        viewModelStateFlow.update { it.copy(isLoadingMore = true, loadMoreErrorMessage = null) }

        viewModelScope.launch {
            val result = api.accounts(cursor = cursor, limit = PAGE_SIZE)
            viewModelStateFlow.update { state ->
                when (result) {
                    is AccountsResult.Success -> {
                        val prev = state.accounts as? AccountsResult.Success
                        val merged = if (prev == null) {
                            result
                        } else {
                            AccountsResult.Success(
                                accounts = prev.accounts + result.accounts,
                                hasMore = result.hasMore,
                                nextCursor = result.nextCursor,
                            )
                        }
                        state.copy(isLoadingMore = false, accounts = merged, loadMoreErrorMessage = null)
                    }

                    is AccountsResult.Failure -> {
                        state.copy(isLoadingMore = false, loadMoreErrorMessage = result.message)
                    }
                }
            }
        }
    }

    private fun createContent(state: ViewModelState): HomeScreenUiState.Content {
        if (state.isLoading && state.accounts == null) {
            return HomeScreenUiState.Content.Loading
        }

        return when (val accounts = state.accounts) {
            null -> HomeScreenUiState.Content.Loading

            is AccountsResult.Failure -> HomeScreenUiState.Content.Error(accounts.message)

            is AccountsResult.Success -> {
                HomeScreenUiState.Content.Loaded(
                    accounts =
                    accounts.accounts.map { account ->
                        HomeScreenUiState.Account(
                            username = account.username,
                            acct = account.acct,
                            onClick = { navigator.navigate(Screen.Account(account.username)) },
                        )
                    },
                    hasMore = accounts.hasMore,
                    isLoadingMore = state.isLoadingMore,
                    loadMoreErrorMessage = state.loadMoreErrorMessage,
                )
            }
        }
    }

    private data class ViewModelState(
        val isLoading: Boolean = false,
        val isLoadingMore: Boolean = false,
        val accounts: AccountsResult? = null,
        val loadMoreErrorMessage: String? = null,
    )

    private companion object {
        const val PAGE_SIZE = 20
    }
}
