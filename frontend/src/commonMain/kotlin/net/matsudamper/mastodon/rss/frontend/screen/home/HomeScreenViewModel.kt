package net.matsudamper.mastodon.rss.frontend.screen.home

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
import net.matsudamper.mastodon.rss.frontend.logic.account.AccountApi
import net.matsudamper.mastodon.rss.frontend.logic.account.AccountsResult
import net.matsudamper.mastodon.rss.frontend.logic.account.HomeAccount
import net.matsudamper.mastodon.rss.frontend.logic.account.NoteLinkPreview
import net.matsudamper.mastodon.rss.frontend.logic.account.NoteLinkPreviewsResult
import net.matsudamper.mastodon.rss.frontend.logic.account.TimelineNote
import net.matsudamper.mastodon.rss.frontend.logic.account.TimelineResult
import net.matsudamper.mastodon.rss.frontend.navigation.Screen

class HomeScreenViewModel(
    private val viewModelScope: CoroutineScope,
    private val api: AccountApi = AccountApi(),
) {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState())

    private val timelinePaging = api.timeline(limit = TIMELINE_PAGE_SIZE)

    // 続きは取らない。全部は一覧の画面で見る
    private val accountsPaging = api.accounts(limit = ACCOUNTS_PREVIEW_SIZE)

    private var timelineJob: Job? = null
    private var loadMoreJob: Job? = null
    private var accountsJob: Job? = null

    val uiStateFlow: StateFlow<HomeScreenUiState> =
        MutableStateFlow(
            HomeScreenUiState(
                timeline = HomeScreenUiState.Timeline.Loading,
                accounts = HomeScreenUiState.Accounts.Loading,
                listener =
                object : HomeScreenUiState.Listener {
                    override fun onClickHome() {
                        navigate(Screen.Home)
                    }

                    override fun onClickAdmin() {
                        navigate(Screen.Admin)
                    }

                    override fun onClickReloadTimeline() {
                        reloadTimeline()
                    }

                    override fun onLoadMore() {
                        loadMore()
                    }

                    override fun onClickReloadAccounts() {
                        reloadAccounts()
                    }

                    override fun onClickAllAccounts() {
                        navigate(Screen.Accounts)
                    }
                },
            ),
        ).also { uiStateFlow ->
            viewModelScope.launch {
                viewModelStateFlow.collect { viewModelState ->
                    uiStateFlow.update { uiState ->
                        uiState.copy(
                            timeline = createTimeline(viewModelState),
                            accounts = createAccounts(viewModelState),
                        )
                    }
                }
            }
        }.asStateFlow()

    fun onStart() {
        val state = viewModelStateFlow.value
        if (state.timeline == null && !state.timelineLoading) {
            reloadTimeline()
        }
        if (state.accounts == null && !state.accountsLoading) {
            reloadAccounts()
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
    private fun reloadTimeline() {
        loadMoreJob?.cancel()
        viewModelStateFlow.update {
            it.copy(
                timeline = null,
                timelineLoading = true,
                loadingMore = false,
                loadMoreErrorMessage = null,
            )
        }

        timelineJob?.cancel()
        timelineJob = viewModelScope.launch {
            timelinePaging.watch().collect { result ->
                viewModelStateFlow.update {
                    it.copy(
                        timelineLoading = false,
                        timeline = result,
                    )
                }
            }
        }
    }

    private fun loadMore() {
        val currentState = viewModelStateFlow.value
        val timeline = currentState.timeline as? TimelineResult.Success ?: return
        if (currentState.loadingMore) return

        val cursor = timeline.cursor ?: return
        viewModelStateFlow.update { it.copy(loadingMore = true, loadMoreErrorMessage = null) }

        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            when (val result = timelinePaging.loadMore(cursor)) {
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

    private fun reloadAccounts() {
        viewModelStateFlow.update { it.copy(accounts = null, accountsLoading = true) }

        accountsJob?.cancel()
        accountsJob = viewModelScope.launch {
            accountsPaging.watch().collect { result ->
                viewModelStateFlow.update {
                    it.copy(
                        accountsLoading = false,
                        accounts = result,
                    )
                }
            }
        }
    }

    /**
     * 1 つの投稿につき 1 回だけ取る。失敗しても取り直さず、OGP が無いものとして出す
     */
    private fun loadLinkPreviews(username: String, noteId: String) {
        if (noteId in viewModelStateFlow.value.linkPreviewRequestedNoteIds) return
        viewModelStateFlow.update {
            it.copy(linkPreviewRequestedNoteIds = it.linkPreviewRequestedNoteIds + noteId)
        }

        viewModelScope.launch {
            when (val result = api.linkPreviews(username = username, id = noteId)) {
                is NoteLinkPreviewsResult.Success -> {
                    viewModelStateFlow.update {
                        it.copy(linkPreviews = it.linkPreviews + (noteId to result.previews))
                    }
                }

                is NoteLinkPreviewsResult.Failure -> Unit
            }
        }
    }

    private fun createTimeline(state: ViewModelState): HomeScreenUiState.Timeline {
        return when (val timeline = state.timeline) {
            null -> HomeScreenUiState.Timeline.Loading

            is TimelineResult.Failure -> HomeScreenUiState.Timeline.Error(timeline.message)

            is TimelineResult.Success -> {
                HomeScreenUiState.Timeline.Loaded(
                    notes = timeline.notes.map { it.toUiState(state.linkPreviews[it.note.id].orEmpty()) },
                    loadMoreVisible = timeline.cursor != null,
                    loadMoreOnVisible = timeline.cursor != null && !state.loadingMore && state.loadMoreErrorMessage == null,
                    loadMoreErrorMessage = state.loadMoreErrorMessage,
                )
            }
        }
    }

    private fun createAccounts(state: ViewModelState): HomeScreenUiState.Accounts {
        return when (val accounts = state.accounts) {
            null -> HomeScreenUiState.Accounts.Loading

            is AccountsResult.Failure -> HomeScreenUiState.Accounts.Error(accounts.message)

            is AccountsResult.Success -> {
                HomeScreenUiState.Accounts.Loaded(
                    accounts = accounts.accounts.map { it.toUiState() },
                )
            }
        }
    }

    private fun TimelineNote.toUiState(linkPreviews: List<NoteLinkPreview>): HomeScreenUiState.Note {
        return HomeScreenUiState.Note(
            url = note.url,
            contentHtml = note.contentHtml,
            publishedAt = UnixTimeUtil.format(note.publishedAt.epochSeconds),
            account = account.toUiState(),
            linkPreviews = linkUrls.map { url ->
                createLinkPreview(url = url, preview = linkPreviews.firstOrNull { it.url == url })
            },
            listener = object : HomeScreenUiState.Note.Listener {
                override fun onClick() {
                    navigate(Screen.AccountNote(username = account.username, noteId = note.id))
                }

                override fun onVisible() {
                    loadLinkPreviews(username = account.username, noteId = note.id)
                }
            },
        )
    }

    /**
     * @param preview まだ取れていなければ null。枠は先に出しておき、取れたら中身だけ差し替える
     */
    private fun createLinkPreview(
        url: String,
        preview: NoteLinkPreview?,
    ): HomeScreenUiState.LinkPreview {
        return HomeScreenUiState.LinkPreview(
            url = url,
            title = preview?.title ?: url,
            siteName = preview?.siteName ?: url.substringAfter("://").substringBefore('/'),
            imageUrl = preview?.imageUrl,
        )
    }

    private fun HomeAccount.toUiState(): HomeScreenUiState.Account {
        return HomeScreenUiState.Account(
            username = username,
            acct = acct,
            displayName = displayName.ifEmpty { username },
            iconUrl = iconUrl,
            listener = object : HomeScreenUiState.Account.Listener {
                override fun onClick() {
                    navigate(Screen.Account(username))
                }
            },
        )
    }

    private data class ViewModelState(
        val timelineLoading: Boolean = false,
        val timeline: TimelineResult? = null,
        val loadingMore: Boolean = false,
        val loadMoreErrorMessage: String? = null,
        val accountsLoading: Boolean = false,
        val accounts: AccountsResult? = null,
        val linkPreviewRequestedNoteIds: Set<String> = setOf(),
        val linkPreviews: Map<String, List<NoteLinkPreview>> = mapOf(),
    )

    interface Event {
        suspend fun navigate(screen: Screen)
    }

    private companion object {
        const val TIMELINE_PAGE_SIZE = 20

        /**
         * トップに出すアカウントの数。残りは一覧の画面で見る
         */
        const val ACCOUNTS_PREVIEW_SIZE = 8
    }
}
