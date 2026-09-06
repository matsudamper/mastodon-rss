package net.matsudamper.mastodon.rss.frontend.screen.account

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.mastodon.rss.frontend.event.EventSender
import net.matsudamper.mastodon.rss.frontend.logic.PagingLoadMoreResult
import net.matsudamper.mastodon.rss.frontend.logic.account.AccountApi
import net.matsudamper.mastodon.rss.frontend.logic.account.AccountFollower
import net.matsudamper.mastodon.rss.frontend.logic.account.AccountFollowersResult

class AccountFollowersScreenViewModel(
    private val username: String,
    private val viewModelScope: CoroutineScope,
    private val api: AccountApi = AccountApi(),
) {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()

    private val followersPaging = api.followers(username = username, limit = PAGE_SIZE)

    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState())

    private var followersJob: Job? = null

    private var loadMoreJob: Job? = null

    // uiStateFlow より後ろに置くと、初期値を組み立てる時点でまだ入っていない
    private val listener = object : AccountFollowersScreenUiState.Listener {
        override fun onClickClose() {
            viewModelScope.launch { events.send { it.close() } }
        }

        override fun onClickReload() {
            reload()
        }

        override fun onClickLoadMore() {
            loadMore()
        }
    }

    val uiStateFlow: StateFlow<AccountFollowersScreenUiState> =
        MutableStateFlow(createUiState(ViewModelState()))
            .also { uiStateFlow ->
                viewModelScope.launch {
                    viewModelStateFlow.collect { viewModelState ->
                        uiStateFlow.update { createUiState(viewModelState) }
                    }
                }
            }.asStateFlow()

    fun onStart() {
        reload()
    }

    /**
     * 一覧は先頭のページを watch して受け取る。続きを足したときもここに流れてくる
     */
    private fun reload() {
        loadMoreJob?.cancel()
        followersJob?.cancel()
        viewModelStateFlow.update { ViewModelState() }
        followersJob = viewModelScope.launch {
            followersPaging.watch().collect { result ->
                when (result) {
                    is AccountFollowersResult.Success -> {
                        viewModelStateFlow.update {
                            it.copy(
                                followers = result.followers,
                                cursor = result.cursor,
                                loaded = true,
                                error = null,
                                loadMoreError = null,
                                loadingMore = false,
                            )
                        }
                    }

                    AccountFollowersResult.NotFound -> {
                        viewModelStateFlow.update { it.copy(notFound = true, loadingMore = false) }
                    }

                    is AccountFollowersResult.Failure -> {
                        viewModelStateFlow.update { it.copy(error = result.message, loadingMore = false) }
                    }
                }
            }
        }
    }

    private fun loadMore() {
        val cursor = viewModelStateFlow.value.cursor ?: return
        if (viewModelStateFlow.value.loadingMore) return

        viewModelStateFlow.update { it.copy(loadingMore = true) }

        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            when (val result = followersPaging.loadMore(cursor)) {
                // 足した一覧は watch から流れてくるので、ここでは待っている印だけ下ろす
                PagingLoadMoreResult.Success -> {
                    viewModelStateFlow.update { it.copy(loadMoreError = null, loadingMore = false) }
                }

                is PagingLoadMoreResult.Failure -> {
                    viewModelStateFlow.update { it.copy(loadMoreError = result.message, loadingMore = false) }
                }
            }
        }
    }

    private fun createUiState(state: ViewModelState): AccountFollowersScreenUiState {
        val content = when {
            state.notFound -> AccountFollowersScreenUiState.Content.NotFound

            // 一度出せているなら、後から来た失敗で一覧ごと消さない
            state.error != null && !state.loaded -> AccountFollowersScreenUiState.Content.Error(state.error)

            !state.loaded -> AccountFollowersScreenUiState.Content.Loading

            state.followers.isEmpty() -> AccountFollowersScreenUiState.Content.Empty

            else -> AccountFollowersScreenUiState.Content.Loaded(
                followers = state.followers.map { follower ->
                    FollowerUiState(
                        actorUrl = follower.actorUrl,
                        listener = object : FollowerUiState.Listener {
                            override fun onClick() {
                                viewModelScope.launch {
                                    events.send { it.openExternalLink(follower.actorUrl) }
                                }
                            }
                        },
                    )
                },
                loadMoreButtonVisible = state.cursor != null,
                loadMoreButtonLoading = state.loadingMore,
                loadMoreErrorMessage = state.loadMoreError,
            )
        }

        return AccountFollowersScreenUiState(
            content = content,
            listener = listener,
        )
    }

    /**
     * @param loaded 1 ページ目を取れたか。取れる前と、1 人もいないのとを分ける
     * @param cursor 続きを取るときに渡す。null なら最後まで取れている
     * @param error 1 ページ目を取れなかった理由
     * @param loadMoreError 続きを取れなかった理由。1 ページ目とは分けて持ち、取れた分は消さない
     */
    private data class ViewModelState(
        val followers: List<AccountFollower> = listOf(),
        val cursor: String? = null,
        val loaded: Boolean = false,
        val notFound: Boolean = false,
        val loadingMore: Boolean = false,
        val error: String? = null,
        val loadMoreError: String? = null,
    )

    private companion object {
        const val PAGE_SIZE: Int = 20
    }

    interface Event {
        /**
         * 1 画面として積んでいるので、戻るのと同じ
         */
        suspend fun close()

        suspend fun openExternalLink(url: String)
    }
}
