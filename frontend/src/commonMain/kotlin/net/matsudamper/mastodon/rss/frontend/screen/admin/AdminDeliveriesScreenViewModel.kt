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
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminAccountRetryingDeliveriesResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminApi
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminDeliveryKind
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminSessionResult
import net.matsudamper.mastodon.rss.frontend.navigation.Screen

class AdminDeliveriesScreenViewModel(
    private val viewModelScope: CoroutineScope,
    private val api: AdminApi = AdminApi(),
) {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState())
    private val deliveriesPaging = api.retryingDeliveries(limit = PAGE_SIZE)
    private var sessionJob: Job? = null
    private var deliveriesJob: Job? = null
    private var loadMoreJob: Job? = null

    val uiStateFlow: StateFlow<AdminDeliveriesScreenUiState> =
        MutableStateFlow(
            AdminDeliveriesScreenUiState(
                content = AdminDeliveriesScreenUiState.Content.Loading,
                listener = object : AdminDeliveriesScreenUiState.Listener {
                    override fun onClickHome() {
                        navigate(Screen.Home)
                    }

                    override fun onClickAdmin() {
                        navigate(Screen.Admin)
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
        deliveriesJob?.cancel()
        loadMoreJob?.cancel()
        deliveriesJob = null
        viewModelStateFlow.update { ViewModelState() }
        sessionJob = viewModelScope.launch {
            api.session().collect { session ->
                viewModelStateFlow.update { it.copy(session = session) }

                if (session is AdminSessionResult.Success && session.loggedIn) {
                    if (deliveriesJob == null) {
                        deliveriesJob = viewModelScope.launch {
                            deliveriesPaging.watch().collect { deliveries ->
                                viewModelStateFlow.update { it.copy(deliveries = deliveries) }
                            }
                        }
                    }
                } else {
                    deliveriesJob?.cancel()
                    loadMoreJob?.cancel()
                    deliveriesJob = null
                    viewModelStateFlow.update {
                        it.copy(deliveries = null, loadingMore = false, loadMoreErrorMessage = null)
                    }
                }
            }
        }
    }

    private fun loadMore() {
        val state = viewModelStateFlow.value
        val deliveries = state.deliveries as? AdminAccountRetryingDeliveriesResult.Success ?: return
        if (state.loadingMore) return
        val cursor = deliveries.nextCursor ?: return

        viewModelStateFlow.update { it.copy(loadingMore = true, loadMoreErrorMessage = null) }

        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            when (val result = deliveriesPaging.loadMore(cursor)) {
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

    private fun createContent(state: ViewModelState): AdminDeliveriesScreenUiState.Content {
        val session = state.session ?: return AdminDeliveriesScreenUiState.Content.Loading

        when (session) {
            is AdminSessionResult.Failure -> {
                return AdminDeliveriesScreenUiState.Content.Error(session.message)
            }

            is AdminSessionResult.Success -> {
                if (!session.loggedIn) return AdminDeliveriesScreenUiState.Content.RequireLogin
            }
        }

        return when (val deliveries = state.deliveries) {
            null -> AdminDeliveriesScreenUiState.Content.Loading

            is AdminAccountRetryingDeliveriesResult.Failure ->
                AdminDeliveriesScreenUiState.Content.Error(deliveries.message)

            is AdminAccountRetryingDeliveriesResult.Success -> {
                AdminDeliveriesScreenUiState.Content.Loaded(
                    deliveries = deliveries.deliveries.map { delivery ->
                        AdminDeliveriesScreenUiState.Delivery(
                            kindText = delivery.kind.toText(),
                            acct = "@${delivery.username}",
                            inbox = delivery.inbox,
                            statusText = if (delivery.sending) {
                                "送っている最中"
                            } else {
                                "次は ${UnixTimeUtil.format(delivery.nextAttemptAt)}"
                            },
                            attemptsText = "${delivery.attempts} 回失敗",
                            lastError = delivery.lastError,
                        )
                    },
                    emptyText = "送り直しを待っている配信は無い。".takeIf { deliveries.deliveries.isEmpty() },
                    loadMoreVisible = deliveries.hasMore,
                    loadingMore = state.loadingMore,
                    loadMoreErrorMessage = state.loadMoreErrorMessage,
                    loadMoreButtonText = if (state.loadMoreErrorMessage != null) "もう一度試す" else "もっと見る",
                )
            }
        }
    }

    /**
     * 配信の種別を画面に出す言葉にする
     */
    private fun AdminDeliveryKind.toText(): String =
        when (this) {
            AdminDeliveryKind.CREATE_NOTE -> "投稿"
            AdminDeliveryKind.DELETE_NOTE -> "投稿の削除"
            AdminDeliveryKind.ACCEPT_FOLLOW -> "フォローの承認"
            AdminDeliveryKind.UPDATE_ACTOR -> "アカウント情報の更新"
            AdminDeliveryKind.DELETE_ACTOR -> "アカウントの削除"
            AdminDeliveryKind.UNKNOWN -> "不明"
        }

    private data class ViewModelState(
        val session: AdminSessionResult? = null,
        val deliveries: AdminAccountRetryingDeliveriesResult? = null,
        val loadingMore: Boolean = false,
        val loadMoreErrorMessage: String? = null,
    )

    interface Event {
        suspend fun navigate(screen: Screen)
    }

    private companion object {
        /**
         * 1 回に取る件数。上限はサーバー側で決まる
         */
        const val PAGE_SIZE = 20
    }
}
