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

    private val listener = object : AccountFollowersScreenUiState.Listener {
        override fun onClickClose() {
            viewModelScope.launch { events.send { it.close() } }
        }
    }

    private val errorListener = object : AccountFollowersScreenUiState.Content.Error.Listener {
        override fun onClickReload() {
            reload()
        }
    }

    private val loadedListener = object : AccountFollowersScreenUiState.Content.Loaded.Listener {
        override fun onLoadMore() {
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

    /**
     * 画面に戻ってくるたびに呼ばれる。取り直すと読み込み中から始まり、スクロール位置が先頭に戻る
     */
    fun onStart() {
        if (followersJob == null) {
            reload()
        }
    }

    private fun reload() {
        loadMoreJob?.cancel()
        followersJob?.cancel()
        viewModelStateFlow.update { ViewModelState() }
        followersJob = viewModelScope.launch {
            followersPaging.watch().collect { result ->
                val followersState = when (result) {
                    is AccountFollowersResult.Success -> FollowersState.Loaded(
                        followers = result.followers,
                        nextCursor = result.nextCursor,
                    )

                    AccountFollowersResult.NotFound -> FollowersState.NotFound

                    is AccountFollowersResult.Failure -> FollowersState.Failure(result.message)
                }

                viewModelStateFlow.update {
                    it.copy(followers = followersState, loadingMore = false, loadMoreError = null)
                }
            }
        }
    }

    private fun loadMore() {
        val nextCursor = (viewModelStateFlow.value.followers as? FollowersState.Loaded)?.nextCursor ?: return
        if (viewModelStateFlow.value.loadingMore) return

        viewModelStateFlow.update { it.copy(loadingMore = true) }

        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            when (val result = followersPaging.loadMore(nextCursor)) {
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
        val content = when (val followersState = state.followers) {
            FollowersState.Loading -> AccountFollowersScreenUiState.Content.Loading

            FollowersState.NotFound -> AccountFollowersScreenUiState.Content.NotFound

            is FollowersState.Failure -> AccountFollowersScreenUiState.Content.Error(
                message = followersState.message,
                listener = errorListener,
            )

            is FollowersState.Loaded -> {
                if (followersState.followers.isEmpty()) {
                    AccountFollowersScreenUiState.Content.Empty
                } else {
                    AccountFollowersScreenUiState.Content.Loaded(
                        followers = followersState.followers.map { follower ->
                            AccountFollowersScreenUiState.Follower(
                                // 表示名を名乗っていない相手がいる。どれも無ければ
                                // プロフィールの URL を出す。空の行が並ぶと誰なのか分からない
                                name = follower.displayName ?: follower.acct ?: follower.url,
                                acct = follower.acct?.takeIf { follower.displayName != null },
                                iconUrl = follower.iconUrl,
                                listener = object : AccountFollowersScreenUiState.Follower.Listener {
                                    override fun onClick() {
                                        viewModelScope.launch {
                                            events.send { it.openExternalLink(follower.url) }
                                        }
                                    }
                                },
                            )
                        },
                        loadMore = when {
                            followersState.nextCursor == null -> AccountFollowersScreenUiState.LoadMore.Hidden
                            state.loadingMore -> AccountFollowersScreenUiState.LoadMore.Loading
                            state.loadMoreError != null -> AccountFollowersScreenUiState.LoadMore.Error(state.loadMoreError)
                            else -> AccountFollowersScreenUiState.LoadMore.LoadOnVisible
                        },
                        listener = loadedListener,
                    )
                }
            }
        }

        return AccountFollowersScreenUiState(
            content = content,
            listener = listener,
        )
    }

    private data class ViewModelState(
        val followers: FollowersState = FollowersState.Loading,
        val loadingMore: Boolean = false,
        val loadMoreError: String? = null,
    )

    private sealed interface FollowersState {
        data object Loading : FollowersState

        data object NotFound : FollowersState

        data class Failure(
            val message: String,
        ) : FollowersState

        data class Loaded(
            val followers: List<AccountFollower>,
            val nextCursor: String?,
        ) : FollowersState
    }

    interface Event {
        /**
         * 1 画面として積んでいるので、戻るのと同じ
         */
        suspend fun close()

        suspend fun openExternalLink(url: String)
    }

    private companion object {
        const val PAGE_SIZE: Int = 20
    }
}
