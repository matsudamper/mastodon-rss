package net.matsudamper.mastodon.rss.frontend.screen.admin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.matsudamper.mastodon.rss.frontend.format.UnixTimeUtil
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminAccount
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminAccountResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminApi
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminDeleteFeedItemResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminFeed
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminFeedItem
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminFeedPreview
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminFeedPreviewResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminNote
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminNotesResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminPostFeedItemsResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminPostNoteResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminSaveFeedResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminSessionResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminUnpublishedFeedItem
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminUnpublishedFeedItemsResult

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
    private var unpublishedJob: Job? = null
    private var postUnpublishedJob: Job? = null
    private var deleteFeedItemJob: Job? = null

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

                    override fun onClickPostLatest() {
                        postUnpublished()
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

                    override fun onClickDeleteFeedItem(id: Long) {
                        deleteFeedItem(id)
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
        unpublishedJob?.cancel()
        postUnpublishedJob?.cancel()
        deleteFeedItemJob?.cancel()
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
            viewModelStateFlow.update { it.copy(account = account) }

            if (account is AdminAccountResult.Success && account.account != null) {
                loadNotes()
                if (account.account.feed != null) {
                    loadUnpublished(account.account.account.id)
                }
            }
        }
    }

    private fun fetchFeed() {
        val state = viewModelStateFlow.value
        val url = state.feedInputUrl.trim()
        if (state.loadedAccount == null) return
        if (url.isEmpty() || state.feedFetching || state.feedSaving || state.savedFeed != null) return

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

                    is AdminFeedPreviewResult.Rejected -> {
                        viewModelStateFlow.update {
                            it.copy(
                                feedFetching = false,
                                feedPreview = null,
                                feedPreviewError = result.reason.toMessage(),
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
        val loaded = state.loadedAccount ?: return
        val accountId = loaded.account.id
        val url = state.feedInputUrl.trim()
        if (url.isEmpty() || state.feedPreview == null || state.feedSaving || state.feedFetching) {
            return
        }

        saveFeedJob?.cancel()
        viewModelStateFlow.update { it.copy(feedSaving = true, feedSaveError = null) }

        saveFeedJob = viewModelScope.launch {
            try {
                when (val result = api.saveFeed(accountId = accountId, url = url)) {
                    is AdminSaveFeedResult.Success -> {
                        viewModelStateFlow.update { state ->
                            state.copy(
                                feedSaving = false,
                                account = state.withSavedFeed(result.feed),
                                feedPreview = null,
                                feedPreviewError = null,
                                feedSaveError = null,
                            )
                        }
                        loadUnpublished(accountId)
                    }

                    is AdminSaveFeedResult.Rejected -> {
                        viewModelStateFlow.update {
                            it.copy(
                                feedSaving = false,
                                feedSaveError = result.reason.toMessage(),
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

    private fun loadUnpublished(accountId: Long) {
        unpublishedJob?.cancel()
        viewModelStateFlow.update { it.copy(unpublishedError = null) }

        unpublishedJob = viewModelScope.launch {
            when (val result = api.unpublishedFeedItems(accountId)) {
                is AdminUnpublishedFeedItemsResult.Success -> {
                    viewModelStateFlow.update {
                        it.copy(
                            unpublishedItems = result.items,
                            unpublishedError = null,
                        )
                    }
                }

                is AdminUnpublishedFeedItemsResult.Rejected -> {
                    viewModelStateFlow.update {
                        it.copy(
                            unpublishedItems = emptyList(),
                            unpublishedError = result.reason.toMessage(),
                        )
                    }
                }

                is AdminUnpublishedFeedItemsResult.Failure -> {
                    viewModelStateFlow.update {
                        it.copy(
                            unpublishedItems = emptyList(),
                            unpublishedError = result.message,
                        )
                    }
                }
            }
        }
    }

    private fun postUnpublished() {
        val accountId = viewModelStateFlow.value.loadedAccount?.account?.id ?: return
        if (viewModelStateFlow.value.postingUnpublished) return

        postUnpublishedJob?.cancel()
        viewModelStateFlow.update { it.copy(postingUnpublished = true, unpublishedError = null, postedItems = null) }

        postUnpublishedJob = viewModelScope.launch {
            try {
                when (val result = api.postFeedItems(accountId)) {
                    is AdminPostFeedItemsResult.Success -> {
                        viewModelStateFlow.update {
                            it.copy(
                                postingUnpublished = false,
                                postedItems = result.items,
                            )
                        }
                        loadUnpublished(accountId)
                        if (result.items.isNotEmpty()) {
                            loadNotes(networkOnly = true)
                        }
                    }

                    is AdminPostFeedItemsResult.Rejected -> {
                        viewModelStateFlow.update {
                            it.copy(
                                postingUnpublished = false,
                                unpublishedError = result.reason.toMessage(),
                            )
                        }
                    }

                    is AdminPostFeedItemsResult.Failure -> {
                        viewModelStateFlow.update {
                            it.copy(
                                postingUnpublished = false,
                                unpublishedError = result.message,
                            )
                        }
                    }
                }
            } finally {
                if (!isActive) {
                    viewModelStateFlow.update { it.copy(postingUnpublished = false) }
                }
            }
        }
    }

    private fun deleteFeedItem(id: Long) {
        val state = viewModelStateFlow.value
        val accountId = state.loadedAccount?.account?.id ?: return
        if (id in state.deletingFeedItemIds) return

        deleteFeedItemJob?.cancel()
        viewModelStateFlow.update {
            it.copy(
                deletingFeedItemIds = it.deletingFeedItemIds + id,
                notesError = null,
            )
        }

        deleteFeedItemJob = viewModelScope.launch {
            try {
                when (val result = api.deleteFeedItem(accountId = accountId, feedItemId = id)) {
                    is AdminDeleteFeedItemResult.Success -> {
                        viewModelStateFlow.update {
                            it.copy(deletingFeedItemIds = it.deletingFeedItemIds - id)
                        }
                        // 消すと未投稿の数も変わる。最新情報の投稿で流れ直すのはこの後
                        loadUnpublished(accountId)
                        loadNotes(networkOnly = true)
                    }

                    is AdminDeleteFeedItemResult.Rejected -> {
                        viewModelStateFlow.update {
                            it.copy(
                                deletingFeedItemIds = it.deletingFeedItemIds - id,
                                notesError = result.reason.toMessage(),
                            )
                        }
                    }

                    is AdminDeleteFeedItemResult.Failure -> {
                        viewModelStateFlow.update {
                            it.copy(
                                deletingFeedItemIds = it.deletingFeedItemIds - id,
                                notesError = result.message,
                            )
                        }
                    }
                }
            } finally {
                if (!isActive) {
                    viewModelStateFlow.update { it.copy(deletingFeedItemIds = it.deletingFeedItemIds - id) }
                }
            }
        }
    }

    /**
     * 投稿の一覧を先頭から取り直す。
     */
    private fun loadNotes(networkOnly: Boolean = false) {
        cancelNotesJobs()
        viewModelStateFlow.update { it.copy(notesLoading = true, notesError = null) }

        notesJob = viewModelScope.launch {
            try {
                when (val result = api.notes(username = username, limit = PAGE_SIZE, networkOnly = networkOnly)) {
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
                        loadNotes(networkOnly = true)
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
                    feed = state.feedUiState(found),
                    post = AdminAccountScreenUiState.Post(
                        body = state.body,
                        submitting = state.submitting,
                        result = state.result,
                        error = state.error,
                    ),
                    notes = state.notes.map { it.toUiState(state.deletingFeedItemIds) },
                    notesError = state.notesError,
                    notesLoading = state.notesLoading,
                    canLoadMore = state.cursor != null,
                    loadingMore = state.loadingMore,
                )
            }
        }
    }

    private fun AdminFeedPreviewResult.PreviewFailure.toMessage(): String =
        when (this) {
            AdminFeedPreviewResult.PreviewFailure.INVALID_URL -> "URL の形式が正しくない"
            AdminFeedPreviewResult.PreviewFailure.FETCH_FAILED -> "フィードを取得できなかった"
            AdminFeedPreviewResult.PreviewFailure.PARSE_FAILED -> "フィードを読み取れなかった"
            AdminFeedPreviewResult.PreviewFailure.UNKNOWN -> "プレビューできなかった"
        }

    private fun AdminSaveFeedResult.SaveFailure.toMessage(): String =
        when (this) {
            AdminSaveFeedResult.SaveFailure.UNKNOWN_ACCOUNT -> "このアカウントには登録できない"
            AdminSaveFeedResult.SaveFailure.DUPLICATE_URL -> "同じ URL は既に登録されている"
            AdminSaveFeedResult.SaveFailure.ALREADY_HAS_FEED -> "このアカウントには既にフィードがある"
            AdminSaveFeedResult.SaveFailure.INVALID_URL -> "URL の形式が正しくない"
            AdminSaveFeedResult.SaveFailure.FETCH_FAILED -> "フィードを取得できなかった"
            AdminSaveFeedResult.SaveFailure.PARSE_FAILED -> "フィードを読み取れなかった"
            AdminSaveFeedResult.SaveFailure.UNKNOWN -> "保存できなかった"
        }

    private fun AdminUnpublishedFeedItemsResult.FailureReason.toMessage(): String =
        when (this) {
            AdminUnpublishedFeedItemsResult.FailureReason.UNKNOWN_ACCOUNT -> "このアカウントは無い"
            AdminUnpublishedFeedItemsResult.FailureReason.NO_FEED -> "フィードが登録されていない"
            AdminUnpublishedFeedItemsResult.FailureReason.UNKNOWN -> "未投稿を取得できなかった"
        }

    private fun AdminPostFeedItemsResult.FailureReason.toMessage(): String =
        when (this) {
            AdminPostFeedItemsResult.FailureReason.UNKNOWN_ACCOUNT -> "このアカウントは無い"
            AdminPostFeedItemsResult.FailureReason.NO_FEED -> "フィードが登録されていない"
            AdminPostFeedItemsResult.FailureReason.INVALID_URL -> "URL の形式が正しくない"
            AdminPostFeedItemsResult.FailureReason.FETCH_FAILED -> "フィードを取得できなかった"
            AdminPostFeedItemsResult.FailureReason.PARSE_FAILED -> "フィードを読み取れなかった"
            AdminPostFeedItemsResult.FailureReason.UNKNOWN -> "未投稿を投稿できなかった"
        }

    private fun AdminDeleteFeedItemResult.FailureReason.toMessage(): String =
        when (this) {
            AdminDeleteFeedItemResult.FailureReason.UNKNOWN_ACCOUNT -> "このアカウントは無い"
            AdminDeleteFeedItemResult.FailureReason.NO_FEED -> "フィードが登録されていない"
            AdminDeleteFeedItemResult.FailureReason.NOT_FOUND -> "この記事は既に消えている"
            AdminDeleteFeedItemResult.FailureReason.UNKNOWN -> "記事を消せなかった"
        }

    private fun AdminUnpublishedFeedItem.toUiState(): AdminAccountScreenUiState.UnpublishedItem =
        AdminAccountScreenUiState.UnpublishedItem(
            title = title,
            link = link,
            publishedAt = publishedAt?.let { UnixTimeUtil.format(it) },
        )

    private fun AdminFeedItem.toUiState(deleting: Boolean): AdminAccountScreenUiState.FeedItem =
        AdminAccountScreenUiState.FeedItem(
            id = id,
            title = title,
            link = link,
            publishedAt = publishedAt?.let { UnixTimeUtil.format(it) },
            stateText = when (state) {
                AdminFeedItem.State.PENDING -> "未投稿"
                AdminFeedItem.State.POSTED -> "投稿済み"
                AdminFeedItem.State.SKIPPED -> "投稿しない"
                AdminFeedItem.State.UNKNOWN -> "不明"
            },
            deleting = deleting,
        )

    private fun ViewModelState.feedUiState(account: AdminAccount): AdminAccountScreenUiState.Feed {
        val feed = account.feed
        return when {
            feed != null -> AdminAccountScreenUiState.Feed.Registered(
                url = feed.url,
                title = feed.title,
                format = feed.format,
                unpublishedItems = unpublishedItems.map { it.toUiState() },
                postedItems = postedItems?.map { it.toUiState() },
                postingUnpublished = postingUnpublished,
                unpublishedError = unpublishedError,
            )

            else -> {
                val busy = feedFetching || feedSaving
                AdminAccountScreenUiState.Feed.Input(
                    url = feedInputUrl,
                    fetching = feedFetching,
                    canFetch = !busy && feedInputUrl.isNotBlank(),
                    saving = feedSaving,
                    canSave = !busy && feedPreview != null,
                    preview = feedPreview?.toUiState(),
                    previewError = feedPreviewError,
                    saveError = feedSaveError,
                )
            }
        }
    }

    private fun AdminAccount.toUiState(): AdminAccountScreenUiState.Account = AdminAccountScreenUiState.Account(
        username = account.username,
        acct = account.acct,
        actorUrl = account.actorUrl,
        createdAt = UnixTimeUtil.format(createdAt),
        followerCount = followerCount,
    )

    private fun AdminFeedPreview.toUiState(): AdminAccountScreenUiState.FeedPreview =
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

    private fun AdminNote.toUiState(deletingFeedItemIds: Set<Long>): AdminAccountScreenUiState.Note =
        AdminAccountScreenUiState.Note(
            url = url,
            contentHtml = contentHtml,
            publishedAt = UnixTimeUtil.format(publishedAt.epochSeconds),
            feedItem = feedItem?.toUiState(deleting = feedItem.id in deletingFeedItemIds),
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
        val feedPreview: AdminFeedPreview? = null,
        val feedPreviewError: String? = null,
        val feedSaving: Boolean = false,
        val feedSaveError: String? = null,
        val deletingFeedItemIds: Set<Long> = emptySet(),
        val unpublishedItems: List<AdminUnpublishedFeedItem> = emptyList(),
        val postedItems: List<AdminUnpublishedFeedItem>? = null,
        val postingUnpublished: Boolean = false,
        val unpublishedError: String? = null,
    ) {
        val loadedAccount: AdminAccount? get() = (account as? AdminAccountResult.Success)?.account

        val savedFeed: AdminFeed? get() = loadedAccount?.feed

        fun withSavedFeed(feed: AdminFeed): AdminAccountResult? {
            val loaded = loadedAccount ?: return account
            return AdminAccountResult.Success(loaded.copy(feed = feed))
        }
    }

    private companion object {
        /**
         * 1 回に取る件数。上限はサーバー側で決まる
         */
        const val PAGE_SIZE = 20
    }
}
