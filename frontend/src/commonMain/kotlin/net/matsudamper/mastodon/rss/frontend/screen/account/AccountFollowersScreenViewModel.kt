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
    }

    private val errorListener = object : AccountFollowersScreenUiState.Content.Error.Listener {
        override fun onClickReload() {
            reload()
        }
    }

    private val loadedListener = object : AccountFollowersScreenUiState.Content.Loaded.Listener {
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
                val followers = when (result) {
                    is AccountFollowersResult.Success -> Followers.Loaded(
                        followers = result.followers,
                        cursor = result.cursor,
                    )

                    AccountFollowersResult.NotFound -> Followers.NotFound

                    is AccountFollowersResult.Failure -> Followers.Failure(result.message)
                }

                viewModelStateFlow.update {
                    it.copy(followers = followers, loadingMore = false, loadMoreError = null)
                }
            }
        }
    }

    private fun loadMore() {
        val cursor = (viewModelStateFlow.value.followers as? Followers.Loaded)?.cursor ?: return
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
        val content = when (val followers = state.followers) {
            Followers.Loading -> AccountFollowersScreenUiState.Content.Loading

            Followers.NotFound -> AccountFollowersScreenUiState.Content.NotFound

            is Followers.Failure -> AccountFollowersScreenUiState.Content.Error(
                message = followers.message,
                listener = errorListener,
            )

            is Followers.Loaded -> {
                if (followers.followers.isEmpty()) {
                    AccountFollowersScreenUiState.Content.Empty
                } else {
                    AccountFollowersScreenUiState.Content.Loaded(
                        followers = followers.followers.map { follower ->
                            AccountFollowersScreenUiState.Follower(
                                actorUrl = follower.actorUrl,
                                listener = object : AccountFollowersScreenUiState.Follower.Listener {
                                    override fun onClick() {
                                        viewModelScope.launch {
                                            events.send { it.openExternalLink(follower.actorUrl) }
                                        }
                                    }
                                },
                            )
                        },
                        loadMore = when {
                            followers.cursor == null -> AccountFollowersScreenUiState.LoadMore.Hidden
                            state.loadingMore -> AccountFollowersScreenUiState.LoadMore.Loading
                            else -> AccountFollowersScreenUiState.LoadMore.Button
                        },
                        loadMoreErrorMessage = state.loadMoreError,
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

    /**
     * @param loadMoreError 続きを取れなかった理由。一覧とは分けて持ち、取れた分は消さない
     */
    private data class ViewModelState(
        val followers: Followers = Followers.Loading,
        val loadingMore: Boolean = false,
        val loadMoreError: String? = null,
    )

    /**
     * 一覧の状態。1 回の問い合わせの結果はこのうちの 1 つなので、分けて持たない
     */
    private sealed interface Followers {
        data object Loading : Followers

        data object NotFound : Followers

        data class Failure(
            val message: String,
        ) : Followers

        /**
         * @param cursor 続きを取るときに渡す。null なら最後まで取れている
         */
        data class Loaded(
            val followers: List<AccountFollower>,
            val cursor: String?,
        ) : Followers
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
