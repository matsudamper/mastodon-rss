package net.matsudamper.mastodon.rss.frontend.screen.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.mastodon.rss.frontend.event.EventSender
import net.matsudamper.mastodon.rss.frontend.logic.PagedQueryLoadMoreResult
import net.matsudamper.mastodon.rss.frontend.logic.account.AccountApi
import net.matsudamper.mastodon.rss.frontend.logic.account.AccountsResult
import net.matsudamper.mastodon.rss.frontend.navigation.Screen

class HomeScreenViewModel(
    private val viewModelScope: CoroutineScope,
    private val api: AccountApi = AccountApi(),
) {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState())

    private var accountsJob: Job? = null
    private var loadMoreJob: Job? = null

    val uiStateFlow: StateFlow<HomeScreenUiState> =
        MutableStateFlow(
            HomeScreenUiState(
                content = HomeScreenUiState.Content.Loading,
                listener =
                object : HomeScreenUiState.Listener {
                    override fun onClickHome() {
                        navigate(Screen.Home)
                    }

                    override fun onClickAdmin() {
                        navigate(Screen.Admin)
                    }

                    override fun onClickReload() {
                        reload()
                    }

                    override fun onClickAccount(username: String) {
                        navigate(Screen.Account(username))
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

    private fun navigate(screen: Screen) {
        viewModelScope.launch {
            events.send { it.navigate(screen) }
        }
    }

    /**
     * 一覧は先頭のページを watch して受け取る。続きを足したときもここに流れてくる
     */
    private fun reload() {
        viewModelStateFlow.update { ViewModelState(isLoading = true) }

        accountsJob?.cancel()
        accountsJob = viewModelScope.launch {
            api.accounts(limit = PAGE_SIZE).collect { result ->
                viewModelStateFlow.update {
                    it.copy(
                        isLoading = false,
                        accounts = result,
                        loadingMore = false,
                    )
                }
            }
        }
    }

    private fun loadMore() {
        val currentState = viewModelStateFlow.value
        val currentAccounts = currentState.accounts as? AccountsResult.Success ?: return
        if (!currentAccounts.hasMore || currentState.loadingMore) return

        val cursor = currentAccounts.nextCursor ?: return
        viewModelStateFlow.update { it.copy(loadingMore = true, loadMoreErrorMessage = null) }

        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            when (val result = api.loadMoreAccounts(cursor = cursor, limit = PAGE_SIZE)) {
                PagedQueryLoadMoreResult.Success -> {
                    viewModelStateFlow.update { it.copy(loadingMore = false, loadMoreErrorMessage = null) }
                }

                // 続きが取れなくても既に出ている一覧は消さない
                is PagedQueryLoadMoreResult.Failure -> {
                    viewModelStateFlow.update { it.copy(loadingMore = false, loadMoreErrorMessage = result.message) }
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
                        )
                    },
                    loadMoreVisible = accounts.hasMore,
                    loadingMore = state.loadingMore,
                    loadMoreErrorMessage = state.loadMoreErrorMessage,
                )
            }
        }
    }

    private data class ViewModelState(
        val isLoading: Boolean = false,
        val loadingMore: Boolean = false,
        val accounts: AccountsResult? = null,
        val loadMoreErrorMessage: String? = null,
    )

    interface Event {
        suspend fun navigate(screen: Screen)
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
