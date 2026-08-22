package net.matsudamper.mastodon.rss.frontend.screen.admin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.mastodon.rss.frontend.format.UnixTimeUtil
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminAccountsResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminApi
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminSessionResult

class AdminAccountsScreenViewModel(
    private val viewModelScope: CoroutineScope,
    private val api: AdminApi = AdminApi(),
) {
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState())

    val uiStateFlow: StateFlow<AdminAccountsScreenUiState> =
        MutableStateFlow(
            AdminAccountsScreenUiState(
                content = AdminAccountsScreenUiState.Content.Loading,
                listener =
                object : AdminAccountsScreenUiState.Listener {
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
        reload()
    }

    private fun reload() {
        viewModelStateFlow.update { ViewModelState(isLoading = true) }
        viewModelScope.launch {
            val session = api.session()
            viewModelStateFlow.update { it.copy(session = session, isLoading = false) }

            if (session is AdminSessionResult.Success && session.loggedIn) {
                viewModelStateFlow.update { it.copy(accounts = api.accounts()) }
            }
        }
    }

    private fun loadMore() {
        val currentState = viewModelStateFlow.value
        val currentAccounts = currentState.accounts as? AdminAccountsResult.Success ?: return
        if (!currentAccounts.hasMore || currentState.isLoadingMore) return

        val cursor = currentAccounts.nextCursor ?: return
        viewModelStateFlow.update { it.copy(isLoadingMore = true, loadMoreErrorMessage = null) }

        viewModelScope.launch {
            val result = api.accounts(cursor = cursor)
            viewModelStateFlow.update { state ->
                when (result) {
                    is AdminAccountsResult.Success -> {
                        val prev = state.accounts as? AdminAccountsResult.Success
                        val merged =
                            if (prev == null) {
                                result
                            } else {
                                AdminAccountsResult.Success(
                                    accounts = prev.accounts + result.accounts,
                                    hasMore = result.hasMore,
                                    nextCursor = result.nextCursor,
                                )
                            }
                        state.copy(isLoadingMore = false, accounts = merged, loadMoreErrorMessage = null)
                    }

                    is AdminAccountsResult.Failure -> {
                        state.copy(isLoadingMore = false, loadMoreErrorMessage = result.message)
                    }
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

        if (state.isLoading && state.accounts == null) {
            return AdminAccountsScreenUiState.Content.Loading
        }

        return when (val accounts = state.accounts) {
            null -> AdminAccountsScreenUiState.Content.Loading

            is AdminAccountsResult.Failure -> AdminAccountsScreenUiState.Content.Error(accounts.message)

            is AdminAccountsResult.Success -> {
                AdminAccountsScreenUiState.Content.Loaded(
                    accounts =
                    accounts.accounts.map { account ->
                        AdminAccountsScreenUiState.Account(
                            username = account.account.username,
                            acct = account.account.acct,
                            actorUrl = account.account.actorUrl,
                            createdAt = account.createdAt?.let { UnixTimeUtil.format(it) },
                            followerCount = account.followerCount,
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
        val session: AdminSessionResult? = null,
        val accounts: AdminAccountsResult? = null,
        val loadMoreErrorMessage: String? = null,
    )
}
