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
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminFeed
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminFeedPreview
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminFeedPreviewResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminNote
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminNotesResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminPostNoteResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminSaveFeedResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminSessionResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminUpdateAccountProfileResult
import net.matsudamper.mastodon.rss.frontend.logic.account.Account

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
    private var profileJob: Job? = null
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
                                feedProfileOverwriteConfirm = null,
                            )
                        }
                    }

                    override fun onFeedProfileDisplayNameChanged(text: String) {
                        viewModelStateFlow.update {
                            it.copy(
                                feedProfileDisplayName = text,
                                feedSaveError = null,
                                feedProfileOverwriteConfirm = null,
                            )
                        }
                    }

                    override fun onFeedProfileSummaryChanged(text: String) {
                        viewModelStateFlow.update {
                            it.copy(
                                feedProfileSummary = text,
                                feedSaveError = null,
                                feedProfileOverwriteConfirm = null,
                            )
                        }
                    }

                    override fun onClickFetchFeed() {
                        fetchFeed()
                    }

                    override fun onClickSaveFeed() {
                        requestSaveFeed()
                    }

                    override fun onClickConfirmProfileOverwrite() {
                        performSaveFeed(updateProfile = true)
                    }

                    override fun onClickSkipProfileOverwrite() {
                        performSaveFeed(updateProfile = false)
                    }

                    override fun onClickEditProfile() {
                        startProfileEdit()
                    }

                    override fun onClickCancelProfileEdit() {
                        cancelProfileEdit()
                    }

                    override fun onProfileDisplayNameChanged(text: String) {
                        viewModelStateFlow.update {
                            it.copy(profileEditDisplayName = text, profileError = null)
                        }
                    }

                    override fun onProfileSummaryChanged(text: String) {
                        viewModelStateFlow.update {
                            it.copy(profileEditSummary = text, profileError = null)
                        }
                    }

                    override fun onClickSaveProfile() {
                        saveProfile()
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
        profileJob?.cancel()
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
                val next = state.copy(account = account)
                if (account is AdminAccountResult.Success && account.account != null) {
                    next.withFeedProfileFrom(account.account.account)
                } else {
                    next
                }
            }

            if (account is AdminAccountResult.Success && account.account != null) {
                loadNotes()
            }
        }
    }

    private fun fetchFeed() {
        val state = viewModelStateFlow.value
        val url = state.feedInputUrl.trim()
        if (state.loadedAccount?.account?.id == null) return
        if (url.isEmpty() || state.feedFetching || state.feedSaving || state.savedFeed != null) return

        fetchFeedJob?.cancel()
        viewModelStateFlow.update {
            it.copy(
                feedFetching = true,
                feedPreview = null,
                feedPreviewError = null,
                feedSaveError = null,
                feedProfileOverwriteConfirm = null,
            )
        }

        fetchFeedJob = viewModelScope.launch {
            try {
                when (val result = api.previewFeed(url)) {
                    is AdminFeedPreviewResult.Success -> {
                        viewModelStateFlow.update { current ->
                            val preview = result.preview
                            var displayName = current.feedProfileDisplayName
                            var summary = current.feedProfileSummary

                            if (displayName == current.feedProfileInitialDisplayName && preview.title != null) {
                                displayName = preview.title
                            }
                            if (summary == current.feedProfileInitialSummary && preview.description != null) {
                                summary = preview.description
                            }

                            current.copy(
                                feedFetching = false,
                                feedPreview = preview,
                                feedPreviewError = null,
                                feedProfileDisplayName = displayName,
                                feedProfileSummary = summary,
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

    private fun requestSaveFeed() {
        val state = viewModelStateFlow.value
        if (!canSaveFeed(state)) return

        if (needsProfileOverwriteConfirm(state)) {
            viewModelStateFlow.update {
                it.copy(
                    feedProfileOverwriteConfirm = ProfileOverwriteConfirmState(
                        beforeDisplayName = it.feedProfileInitialDisplayName,
                        beforeSummary = it.feedProfileInitialSummary,
                        afterDisplayName = it.feedProfileDisplayName.trim(),
                        afterSummary = it.feedProfileSummary.trim(),
                    ),
                )
            }
            return
        }

        performSaveFeed(updateProfile = shouldUpdateProfileOnSave(state))
    }

    private fun performSaveFeed(updateProfile: Boolean) {
        val state = viewModelStateFlow.value
        val accountId = state.loadedAccount?.account?.id
        val url = state.feedInputUrl.trim()
        if (accountId == null || url.isEmpty() || state.feedPreview == null || state.feedSaving || state.feedFetching) {
            return
        }

        saveFeedJob?.cancel()
        viewModelStateFlow.update {
            it.copy(
                feedSaving = true,
                feedSaveError = null,
                feedProfileOverwriteConfirm = null,
            )
        }

        saveFeedJob = viewModelScope.launch {
            try {
                when (val result = api.saveFeed(accountId = accountId, url = url)) {
                    is AdminSaveFeedResult.Success -> {
                        if (updateProfile) {
                            updateProfileAfterFeedSave(
                                displayName = state.feedProfileDisplayName.trim(),
                                summary = state.feedProfileSummary.trim(),
                                feed = result.feed,
                            )
                        } else {
                            viewModelStateFlow.update { current ->
                                current.copy(
                                    feedSaving = false,
                                    account = current.withSavedFeed(result.feed),
                                    feedPreview = null,
                                    feedPreviewError = null,
                                    feedSaveError = null,
                                )
                            }
                        }
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

    private suspend fun updateProfileAfterFeedSave(
        displayName: String,
        summary: String,
        feed: AdminFeed,
    ) {
        when (
            val result = api.updateAccountProfile(
                username = username,
                displayName = displayName,
                summary = summary,
            )
        ) {
            is AdminUpdateAccountProfileResult.Success -> {
                viewModelStateFlow.update { state ->
                    state.copy(
                        feedSaving = false,
                        account = AdminAccountResult.Success(result.adminAccount),
                        feedPreview = null,
                        feedPreviewError = null,
                        feedSaveError = null,
                    ).withFeedProfileFrom(result.adminAccount.account)
                }
            }

            is AdminUpdateAccountProfileResult.Rejected -> {
                viewModelStateFlow.update { state ->
                    state.copy(
                        feedSaving = false,
                        account = state.withSavedFeed(feed),
                        feedSaveError = profileRejectedMessage(result),
                    )
                }
            }

            is AdminUpdateAccountProfileResult.Failure -> {
                viewModelStateFlow.update { state ->
                    state.copy(
                        feedSaving = false,
                        account = state.withSavedFeed(feed),
                        feedSaveError = result.message,
                    )
                }
            }
        }
    }

    private fun canSaveFeed(state: ViewModelState): Boolean {
        val busy = state.feedFetching || state.feedSaving
        return !busy &&
            state.feedPreview != null &&
            state.feedProfileDisplayName.trim().isNotEmpty()
    }

    private fun needsProfileOverwriteConfirm(state: ViewModelState): Boolean {
        if (!state.feedProfileStored) return false

        val displayName = state.feedProfileDisplayName.trim()
        val summary = state.feedProfileSummary.trim()
        return displayName != state.feedProfileInitialDisplayName.trim() ||
            summary != state.feedProfileInitialSummary.trim()
    }

    private fun shouldUpdateProfileOnSave(state: ViewModelState): Boolean {
        if (!state.feedProfileStored) return true

        val displayName = state.feedProfileDisplayName.trim()
        val summary = state.feedProfileSummary.trim()
        return displayName != state.feedProfileInitialDisplayName.trim() ||
            summary != state.feedProfileInitialSummary.trim()
    }

    /**
     * 投稿の一覧を先頭から取り直す。
     */
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

    private fun startProfileEdit() {
        val account = (viewModelStateFlow.value.account as? AdminAccountResult.Success)?.account ?: return

        viewModelStateFlow.update {
            it.copy(
                profileEditing = true,
                profileEditDisplayName = account.account.displayName,
                profileEditSummary = account.account.summary,
                profileError = null,
            )
        }
    }

    private fun cancelProfileEdit() {
        profileJob?.cancel()
        viewModelStateFlow.update {
            it.copy(
                profileEditing = false,
                profileSaving = false,
                profileError = null,
            )
        }
    }

    private fun saveProfile() {
        val state = viewModelStateFlow.value
        val displayName = state.profileEditDisplayName.trim()
        val summary = state.profileEditSummary.trim()
        if (displayName.isEmpty() || state.profileSaving) return

        profileJob?.cancel()
        viewModelStateFlow.update { it.copy(profileSaving = true, profileError = null) }

        profileJob = viewModelScope.launch {
            try {
                when (
                    val result = api.updateAccountProfile(
                        username = username,
                        displayName = displayName,
                        summary = summary,
                    )
                ) {
                    is AdminUpdateAccountProfileResult.Success -> {
                        viewModelStateFlow.update {
                            it.copy(
                                account = AdminAccountResult.Success(result.adminAccount),
                                profileEditing = false,
                                profileSaving = false,
                                profileError = null,
                            ).withFeedProfileFrom(result.adminAccount.account)
                        }
                    }

                    is AdminUpdateAccountProfileResult.Rejected -> {
                        viewModelStateFlow.update {
                            it.copy(
                                profileSaving = false,
                                profileError = profileRejectedMessage(result),
                            )
                        }
                    }

                    is AdminUpdateAccountProfileResult.Failure -> {
                        viewModelStateFlow.update {
                            it.copy(
                                profileSaving = false,
                                profileError = result.message,
                            )
                        }
                    }
                }
            } finally {
                if (!isActive) {
                    viewModelStateFlow.update { it.copy(profileSaving = false) }
                }
            }
        }
    }

    private fun profileRejectedMessage(rejected: AdminUpdateAccountProfileResult.Rejected): String = buildList {
        if (rejected.unknownAccount) add("このアカウントは応答しない")
        if (rejected.emptyDisplayName) add("表示名が空")
        if (rejected.displayNameMaxLength != null) add("表示名は ${rejected.displayNameMaxLength} 文字まで")
        if (rejected.summaryMaxLength != null) add("説明文は ${rejected.summaryMaxLength} 文字まで")
    }.joinToString("\n").ifEmpty { "保存できなかった" }

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
                    feed = state.feedUiState(found),
                    profile = state.profileUiState(found.account),
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

    private fun ViewModelState.feedUiState(account: AdminAccount): AdminAccountScreenUiState.Feed {
        val feed = account.feed
        return when {
            account.account.id == null -> AdminAccountScreenUiState.Feed.None

            feed != null -> AdminAccountScreenUiState.Feed.Registered(
                url = feed.url,
                title = feed.title,
                format = feed.format,
            )

            else -> {
                val busy = feedFetching || feedSaving
                AdminAccountScreenUiState.Feed.Input(
                    url = feedInputUrl,
                    displayName = feedProfileDisplayName,
                    summary = feedProfileSummary,
                    fetching = feedFetching,
                    canFetch = !busy && feedInputUrl.isNotBlank() && feedProfileOverwriteConfirm == null,
                    saving = feedSaving,
                    canSave = canSaveFeed(this) && feedProfileOverwriteConfirm == null,
                    preview = feedPreview?.toUiState(),
                    previewError = feedPreviewError,
                    saveError = feedSaveError,
                    overwriteConfirm = feedProfileOverwriteConfirm?.toUiState(),
                )
            }
        }
    }

    private fun ViewModelState.profileUiState(account: Account): AdminAccountScreenUiState.Profile? {
        if (savedFeed == null || account.id == null) return null

        return AdminAccountScreenUiState.Profile(
            displayName = account.displayName,
            summary = account.summary,
            editing = profileEditing,
            editDisplayName = if (profileEditing) profileEditDisplayName else account.displayName,
            editSummary = if (profileEditing) profileEditSummary else account.summary,
            saving = profileSaving,
            error = profileError,
        )
    }

    private fun AdminAccount.toUiState(): AdminAccountScreenUiState.Account = AdminAccountScreenUiState.Account(
        username = account.username,
        acct = account.acct,
        actorUrl = account.actorUrl,
        createdAt = createdAt?.let { UnixTimeUtil.format(it) },
        followerCount = followerCount,
        displayName = account.displayName,
        summary = account.summary,
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

    private fun ProfileOverwriteConfirmState.toUiState(): AdminAccountScreenUiState.ProfileOverwriteConfirm =
        AdminAccountScreenUiState.ProfileOverwriteConfirm(
            beforeDisplayName = beforeDisplayName,
            beforeSummary = beforeSummary,
            afterDisplayName = afterDisplayName,
            afterSummary = afterSummary,
        )

    private fun AdminNote.toUiState(): AdminAccountScreenUiState.Note = AdminAccountScreenUiState.Note(
        url = url,
        contentHtml = contentHtml,
        publishedAt = UnixTimeUtil.format(publishedAt.epochSeconds),
    )

    private data class ProfileOverwriteConfirmState(
        val beforeDisplayName: String,
        val beforeSummary: String,
        val afterDisplayName: String,
        val afterSummary: String,
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
        val profileEditing: Boolean = false,
        val profileEditDisplayName: String = "",
        val profileEditSummary: String = "",
        val profileSaving: Boolean = false,
        val profileError: String? = null,
        val feedInputUrl: String = "",
        val feedProfileDisplayName: String = "",
        val feedProfileSummary: String = "",
        val feedProfileStored: Boolean = false,
        val feedProfileInitialDisplayName: String = "",
        val feedProfileInitialSummary: String = "",
        val feedProfileOverwriteConfirm: ProfileOverwriteConfirmState? = null,
        val feedFetching: Boolean = false,
        val feedPreview: AdminFeedPreview? = null,
        val feedPreviewError: String? = null,
        val feedSaving: Boolean = false,
        val feedSaveError: String? = null,
    ) {
        val loadedAccount: AdminAccount? get() = (account as? AdminAccountResult.Success)?.account

        val savedFeed: AdminFeed? get() = loadedAccount?.feed

        fun withSavedFeed(feed: AdminFeed): AdminAccountResult? {
            val loaded = loadedAccount ?: return account
            return AdminAccountResult.Success(loaded.copy(feed = feed))
        }

        fun withFeedProfileFrom(account: Account): ViewModelState = copy(
            feedProfileDisplayName = account.displayName,
            feedProfileSummary = account.summary,
            feedProfileStored = account.profileStored,
            feedProfileInitialDisplayName = account.displayName,
            feedProfileInitialSummary = account.summary,
            feedProfileOverwriteConfirm = null,
        )
    }

    private companion object {
        /**
         * 1 回に取る件数。上限はサーバー側で決まる
         */
        const val PAGE_SIZE = 20
    }
}
