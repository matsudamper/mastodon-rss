package net.matsudamper.mastodon.rss.frontend.screen.admin // pragma: allowlist secret

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.matsudamper.mastodon.rss.frontend.format.UnixTimeUtil // pragma: allowlist secret // pragma: allowlist secret
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminAccount // pragma: allowlist secret // pragma: allowlist secret
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminAccountResult // pragma: allowlist secret // pragma: allowlist secret
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminApi // pragma: allowlist secret // pragma: allowlist secret
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminFeedPreviewResult // pragma: allowlist secret // pragma: allowlist secret
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminNote // pragma: allowlist secret // pragma: allowlist secret
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminNotesResult // pragma: allowlist secret // pragma: allowlist secret
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminPostNoteResult // pragma: allowlist secret // pragma: allowlist secret
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminSaveFeedResult // pragma: allowlist secret // pragma: allowlist secret
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminSessionResult // pragma: allowlist secret // pragma: allowlist secret

class AdminAccountScreenViewModel(
    private val username: String,
    private val viewModelScope: CoroutineScope,
    private val api: AdminApi = AdminApi(),
) {
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState())

    private var reloadJob: Job? = null
    private var notesJob: Job? = null
    private var loadMoreJob: Job? = null
    private var postJob: Job? = null
    private var fetchFeedJob: Job? = null
    private var saveFeedJob: Job? = null

    val uiStateFlow: StateFlow<AdminAccountScreenUiState> =
        MutableStateFlow(
            AdminAccountScreenUiState(
                acct = "@$username",
                content = AdminAccountScreenUiState.Content.Loading,
                listener = object : AdminAccountScreenUiState.Listener {
                    override fun onFeedUrlChanged(text: String) {
                        viewModelStateFlow.update {
                            it.copy(
                                feedInputUrl = text,
                                feedPreview = null,
                                feedPreviewError = null,
                                feedSaveError = null,
                            )
                        }
                    }

                    override fun onClickFetchFeed() {
                        fetchFeed()
                    }

                    override fun onClickSaveFeed() {
                        saveFeed()
                    }

                    override fun onBodyChanged(text: String) {
                        viewModelStateFlow.update { it.copy(body = text, error = null, result = null) }
                    }

                    override fun onClickPost() {
                        post()
                    }

                    override fun onClickLoadMore() {
                        loadMore()
                    }

                    override fun onClickReloadNotes() {
                        loadNotes()
                    }

                    override fun onClickReload() {
                        reload()
                    }
                },
            ),
        ).also { uiStateFlow ->
            viewModelScope.launch {
                viewModelStateFlow.collect { viewModelState ->
                    uiStateFlow.update { uiState -> uiState.copy(content = createContent(viewModelState)) }
                }
            }
        }.asStateFlow()

    fun onStart() {
        reload()
    }

    private fun reload() {
        reloadJob?.cancel()
        postJob?.cancel()
        fetchFeedJob?.cancel()
        saveFeedJob?.cancel()
        cancelNotesJobs()

        viewModelStateFlow.update {
            ViewModelState(
                body = it.body,
                feedInputUrl = it.feedInputUrl,
            )
        }

        reloadJob = viewModelScope.launch {
            val session = api.session()
            viewModelStateFlow.update { it.copy(session = session) }

            if (session !is AdminSessionResult.Success || !session.loggedIn) return@launch

            val account = api.account(username)
            viewModelStateFlow.update { state ->
                state.copy(
                    account = account,
                    registeredFeedUrl = (account as? AdminAccountResult.Success)?.account?.feed?.url,
                    registeredFeedTitle = (account as? AdminAccountResult.Success)?.account?.feed?.title,
                    registeredFeedFormat = (account as? AdminAccountResult.Success)?.account?.feed?.format,
                )
            }

            if (account is AdminAccountResult.Success && account.account != null) {
                loadNotes()
            }
        }
    }

    private fun fetchFeed() {
        val state = viewModelStateFlow.value
        val url = state.feedInputUrl.trim()
        if (url.isEmpty() || state.feedFetching || state.feedSaving || state.registeredFeedUrl != null) return

        fetchFeedJob?.cancel()
        viewModelStateFlow.update {
            it.copy(
                feedFetching = true,
                feedPreview = null,
                feedPreviewError = null,
                feedSaveError = null,
            )
        }

        fetchFeedJob = viewModelScope.launch {
            try {
                when (val result = api.previewFeed(url)) {
                    is AdminFeedPreviewResult.Success -> {
                        viewModelStateFlow.update {
                            it.copy(
                                feedFetching = false,
                                feedPreview = result.preview,
                                feedPreviewError = null,
                            )
                        }
                    }

                    is AdminFeedPreviewResult.Failure -> {
                        viewModelStateFlow.update {
                            it.copy(
                                feedFetching = false,
                                feedPreview = null,
                                feedPreviewError = result.message,
                            )
                        }
                    }
                }
            } finally {
                if (!isActive) {
                    viewModelStateFlow.update { it.copy(feedFetching = false) }
                }
            }
        }
    }

    private fun saveFeed() {
        val state = viewModelStateFlow.value
        val accountId = (state.account as? AdminAccountResult.Success)?.account?.account?.id
        val url = state.feedInputUrl.trim()
        if (accountId == null || url.isEmpty() || state.feedPreview == null || state.feedSaving || state.feedFetching) {
            return
        }

        saveFeedJob?.cancel()
        viewModelStateFlow.update { it.copy(feedSaving = true, feedSaveError = null) }

        saveFeedJob = viewModelScope.launch {
            try {
                when (val result = api.saveFeed(accountId = accountId, url = url)) {
                    is AdminSaveFeedResult.Success -> {
                        viewModelStateFlow.update {
                            it.copy(
                                feedSaving = false,
                                registeredFeedUrl = result.feed.url,
                                registeredFeedTitle = result.feed.title,
                                registeredFeedFormat = result.feed.format,
                                feedPreview = null,
                                feedPreviewError = null,
                                feedSaveError = null,
                            )
                        }
                    }

                    is AdminSaveFeedResult.Failure -> {
                        viewModelStateFlow.update {
                            it.copy(
                                feedSaving = false,
                                feedSaveError = result.message,
                            )
                        }
                    }
                }
            } finally {
                if (!isActive) {
                    viewModelStateFlow.update { it.copy(feedSaving = false) }
                }
            }
        }
    }

    private fun loadNotes() {
        cancelNotesJobs()
        viewModelStateFlow.update { it.copy(notesLoading = true, notesError = null) }

        notesJob = viewModelScope.launch {
            try {
                when (val result = api.notes(username = username, limit = PAGE_SIZE)) {
                    is AdminNotesResult.Success -> {
                        viewModelStateFlow.update {
                            it.copy(
                                notes = result.notes,
                                notesError = null,
                                cursor = result.cursor,
                                loadingMore = false,
                                notesLoading = false,
                            )
                        }
                    }

                    is AdminNotesResult.Failure -> {
                        viewModelStateFlow.update {
                            it.copy(
                                notesError = result.message,
                                loadingMore = false,
                                notesLoading = false,
                            )
                        }
                    }
                }
            } finally {
                if (!isActive) {
                    viewModelStateFlow.update { it.copy(notesLoading = false, loadingMore = false) }
                }
            }
        }
    }

    private fun loadMore() {
        val cursor = viewModelStateFlow.value.cursor ?: return
        if (viewModelStateFlow.value.loadingMore) return

        loadMoreJob?.cancel()
        viewModelStateFlow.update { it.copy(loadingMore = true) }

        loadMoreJob = viewModelScope.launch {
            try {
                when (val result = api.notes(username = username, cursor = cursor, limit = PAGE_SIZE)) {
                    is AdminNotesResult.Success -> {
                        viewModelStateFlow.update { current ->
                            current.copy(
                                notes = current.notes + result.notes,
                                notesError = null,
                                cursor = result.cursor,
                                loadingMore = false,
                            )
                        }
                    }

                    is AdminNotesResult.Failure -> {
                        viewModelStateFlow.update {
                            it.copy(
                                notesError = result.message,
                                loadingMore = false,
                            )
                        }
                    }
                }
            } finally {
                if (!isActive) {
                    viewModelStateFlow.update { it.copy(loadingMore = false) }
                }
            }
        }
    }

    private fun cancelNotesJobs() {
        notesJob?.cancel()
        loadMoreJob?.cancel()
        notesJob = null
        loadMoreJob = null
    }

    private fun post() {
        val state = viewModelStateFlow.value
        val body = state.body.trim()
        if (body.isEmpty() || state.submitting) return

        postJob?.cancel()
        viewModelStateFlow.update { it.copy(submitting = true, error = null, result = null) }

        postJob = viewModelScope.launch {
            try {
                when (val result = api.postNote(username = username, body = body)) {
                    is AdminPostNoteResult.Success -> {
                        viewModelStateFlow.update {
                            it.copy(
                                body = "",
                                submitting = false,
                                result = AdminAccountScreenUiState.PostResult(
                                    url = result.note.url,
                                    targets = result.deliveryTargets,
                                    delivered = result.delivered,
                                ),
                                error = null,
                            )
                        }
                        loadNotes()
                    }

                    is AdminPostNoteResult.Rejected -> {
                        viewModelStateFlow.update {
                            it.copy(
                                submitting = false,
                                error = rejectedMessage(result),
                            )
                        }
                    }

                    is AdminPostNoteResult.Failure -> {
                        viewModelStateFlow.update { it.copy(submitting = false, error = result.message) }
                    }
                }
            } finally {
                if (!isActive) {
                    viewModelStateFlow.update { it.copy(submitting = false) }
                }
            }
        }
    }

    private fun rejectedMessage(rejected: AdminPostNoteResult.Rejected): String = buildList {
        if (rejected.unknownAccount) add("このアカウントは応答しない")
        if (rejected.isEmpty) add("本文が空")
        if (rejected.maxLength != null) add("${rejected.maxLength} 文字までにする")
    }.joinToString("\n").ifEmpty { "投稿できなかった" }

    private fun createContent(state: ViewModelState): AdminAccountScreenUiState.Content {
        val session = state.session ?: return AdminAccountScreenUiState.Content.Loading

        when (session) {
            is AdminSessionResult.Failure -> return AdminAccountScreenUiState.Content.Error(session.message)

            is AdminSessionResult.Success -> {
                if (!session.loggedIn) return AdminAccountScreenUiState.Content.RequireLogin
            }
        }

        return when (val account = state.account) {
            null -> AdminAccountScreenUiState.Content.Loading

            is AdminAccountResult.Failure -> AdminAccountScreenUiState.Content.Error(account.message)

            is AdminAccountResult.Success -> {
                val found = account.account ?: return AdminAccountScreenUiState.Content.NotFound

                AdminAccountScreenUiState.Content.Loaded(
                    account = found.toUiState(),
                    feed = AdminAccountScreenUiState.Feed(
                        registeredUrl = state.registeredFeedUrl,
                        registeredTitle = state.registeredFeedTitle,
                        registeredFormat = state.registeredFeedFormat,
                        inputUrl = state.feedInputUrl,
                        fetching = state.feedFetching,
                        preview = state.feedPreview?.toUiState(),
                        previewError = state.feedPreviewError,
                        saving = state.feedSaving,
                        saveError = state.feedSaveError,
                    ),
                    post = AdminAccountScreenUiState.Post(
                        body = state.body,
                        submitting = state.submitting,
                        result = state.result,
                        error = state.error,
                    ),
                    notes = state.notes.map { it.toUiState() },
                    notesError = state.notesError,
                    notesLoading = state.notesLoading,
                    canLoadMore = state.cursor != null,
                    loadingMore = state.loadingMore,
                )
            }
        }
    }

    private fun AdminAccount.toUiState(): AdminAccountScreenUiState.Account = AdminAccountScreenUiState.Account(
        accountId = account.id,
        username = account.username,
        acct = account.acct,
        actorUrl = account.actorUrl,
        createdAt = createdAt?.let { UnixTimeUtil.format(it) },
        followerCount = followerCount,
    )

    private fun net.matsudamper.mastodon.rss.frontend.logic.admin.AdminFeedPreview.toUiState(): AdminAccountScreenUiState.FeedPreview = // pragma: allowlist secret
        AdminAccountScreenUiState.FeedPreview(
            title = title,
            siteUrl = siteUrl,
            format = format,
            description = description,
            itemCount = itemCount,
            sampleItems = sampleItems.map { item ->
                AdminAccountScreenUiState.FeedPreviewItem(
                    title = item.title,
                    link = item.link,
                    publishedAt = item.publishedAt?.let { UnixTimeUtil.format(it) },
                )
            },
        )

    private fun AdminNote.toUiState(): AdminAccountScreenUiState.Note = AdminAccountScreenUiState.Note(
        url = url,
        contentHtml = contentHtml,
        publishedAt = UnixTimeUtil.format(publishedAt.epochSeconds),
    )

    private data class ViewModelState(
        val session: AdminSessionResult? = null,
        val account: AdminAccountResult? = null,
        val body: String = "",
        val submitting: Boolean = false,
        val result: AdminAccountScreenUiState.PostResult? = null,
        val error: String? = null,
        val notes: List<AdminNote> = emptyList(),
        val notesError: String? = null,
        val notesLoading: Boolean = false,
        val cursor: String? = null,
        val loadingMore: Boolean = false,
        val feedInputUrl: String = "",
        val feedFetching: Boolean = false,
        val feedPreview: net.matsudamper.mastodon.rss.frontend.logic.admin.AdminFeedPreview? = null, // pragma: allowlist secret
        val feedPreviewError: String? = null,
        val feedSaving: Boolean = false,
        val feedSaveError: String? = null,
        val registeredFeedUrl: String? = null,
        val registeredFeedTitle: String? = null,
        val registeredFeedFormat: String? = null,
    )

    private companion object {
        const val PAGE_SIZE = 20
    }
}
