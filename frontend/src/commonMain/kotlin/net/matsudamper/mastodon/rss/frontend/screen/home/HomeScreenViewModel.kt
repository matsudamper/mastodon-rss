package net.matsudamper.mastodon.rss.frontend.screen.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.mastodon.rss.frontend.logic.account.AccountApi
import net.matsudamper.mastodon.rss.frontend.logic.account.AccountsResult

class HomeScreenViewModel(
    private val viewModelScope: CoroutineScope,
    private val api: AccountApi = AccountApi(),
) {
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState())

    val uiStateFlow: StateFlow<HomeScreenUiState> =
        MutableStateFlow(
            HomeScreenUiState(
                content = HomeScreenUiState.Content.Loading,
                listener =
                object : HomeScreenUiState.Listener {
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
        if (viewModelStateFlow.value.accounts == null) {
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
        viewModelStateFlow.update { it.copy(isLoadingMore = true) }

        viewModelScope.launch {
            val result = api.accounts(cursor = cursor, limit = PAGE_SIZE)
            viewModelStateFlow.update { state ->
                val prev = state.accounts as? AccountsResult.Success
                val newResult =
                    when (result) {
                        is AccountsResult.Success -> {
                            if (prev != null) {
                                AccountsResult.Success(
                                    accounts = prev.accounts + result.accounts,
                                    hasMore = result.hasMore,
                                    nextCursor = result.nextCursor,
                                )
                            } else {
                                result
                            }
                        }

                        is AccountsResult.Failure -> {
                            prev ?: result
                        }
                    }
                state.copy(
                    isLoadingMore = false,
                    accounts = newResult,
                )
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
                            actorUrl = account.actorUrl,
                        )
                    },
                    hasMore = accounts.hasMore,
                    isLoadingMore = state.isLoadingMore,
                )
            }
        }
    }

    private data class ViewModelState(
        val isLoading: Boolean = false,
        val isLoadingMore: Boolean = false,
        val accounts: AccountsResult? = null,
    )

    private companion object {
        const val PAGE_SIZE = 20
    }
}
