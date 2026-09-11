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
import net.matsudamper.mastodon.rss.frontend.logic.PagingLoadMoreResult
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
    private val accountsPaging = api.accounts(limit = PAGE_SIZE)
    private var sessionJob: Job? = null
    private var accountsJob: Job? = null
    private var loadMoreJob: Job? = null

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

    private fun navigate(screen: Screen) {
        viewModelScope.launch {
            events.send { it.navigate(screen) }
        }
    }

    /**
     * 一覧は先頭のページを watch して受け取る。続きを足したときもここに流れてくる
     */
    private fun reload() {
        sessionJob?.cancel()
        accountsJob?.cancel()
        loadMoreJob?.cancel()
        accountsJob = null
        viewModelStateFlow.update { ViewModelState() }
        sessionJob = viewModelScope.launch {
            api.session().collect { session ->
                viewModelStateFlow.update { it.copy(session = session) }

                if (session is AdminSessionResult.Success && session.loggedIn) {
                    if (accountsJob == null) {
                        accountsJob = viewModelScope.launch {
                            accountsPaging.watch().collect { accounts ->
                                viewModelStateFlow.update { it.copy(accounts = accounts) }
                            }
                        }
                    }
                } else {
                    accountsJob?.cancel()
                    loadMoreJob?.cancel()
                    accountsJob = null
                    viewModelStateFlow.update { it.copy(accounts = null, loadingMore = false, loadMoreErrorMessage = null) }
                }
            }
        }
    }

    private fun loadMore() {
        val state = viewModelStateFlow.value
        val accounts = state.accounts as? AdminAccountsResult.Success ?: return
        if (state.loadingMore) return
        val cursor = accounts.nextCursor ?: return

        viewModelStateFlow.update { it.copy(loadingMore = true, loadMoreErrorMessage = null) }

        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            when (val result = accountsPaging.loadMore(cursor)) {
                PagingLoadMoreResult.Success -> {
                    viewModelStateFlow.update { it.copy(loadingMore = false, loadMoreErrorMessage = null) }
                }

                // 続きが取れなくても既に出ている一覧は消さない
                is PagingLoadMoreResult.Failure -> {
                    viewModelStateFlow.update { it.copy(loadingMore = false, loadMoreErrorMessage = result.message) }
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
                            displayName = account.account.displayName,
                            acct = account.account.acct,
                            actorUrl = account.account.actorUrl,
                            iconUrl = account.iconUrl,
                            createdAt = UnixTimeUtil.format(account.createdAt),
                            followerCount = account.followerCount,
                        )
                    },
                    loadMoreVisible = accounts.hasMore,
                    loadingMore = state.loadingMore,
                    loadMoreErrorMessage = state.loadMoreErrorMessage,
                    loadMoreButtonText = if (state.loadMoreErrorMessage != null) "もう一度試す" else "もっと見る",
                )
            }
        }
    }

    private data class ViewModelState(
        val session: AdminSessionResult? = null,
        val accounts: AdminAccountsResult? = null,
        val loadingMore: Boolean = false,
        val loadMoreErrorMessage: String? = null,
    )

    interface Event {
        suspend fun navigate(screen: Screen)
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
