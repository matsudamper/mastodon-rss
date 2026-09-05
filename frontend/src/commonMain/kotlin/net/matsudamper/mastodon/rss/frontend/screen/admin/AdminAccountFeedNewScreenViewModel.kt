package net.matsudamper.mastodon.rss.frontend.screen.admin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.matsudamper.mastodon.rss.frontend.event.EventSender
import net.matsudamper.mastodon.rss.frontend.format.UnixTimeUtil
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminAccountResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminApi
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminFeedPreview
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminFeedPreviewResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminFeedUpdates
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminSaveFeedResult

class AdminAccountFeedNewScreenViewModel(
    private val username: String,
    private val viewModelScope: CoroutineScope,
    private val api: AdminApi = AdminApi(),
) {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState())

    private var previewJob: Job? = null
    private var saveJob: Job? = null

    // uiStateFlow より後ろに置くと、初期値を組み立てる時点でまだ入っていない
    private val listener = object : AdminAccountFeedNewScreenUiState.Listener {
        override fun onUrlChanged(text: String) {
            viewModelStateFlow.update {
                it.copy(url = text, preview = null, errorMessage = null)
            }
        }

        override fun onClickFetch() {
            fetchPreview()
        }

        override fun onClickSave() {
            saveAndClose()
        }

        override fun onClickClose() {
            close()
        }
    }

    val uiStateFlow: StateFlow<AdminAccountFeedNewScreenUiState> =
        MutableStateFlow(
            createUiState(ViewModelState()),
        ).also { uiStateFlow ->
            viewModelScope.launch {
                viewModelStateFlow.collect { viewModelState ->
                    uiStateFlow.update { createUiState(viewModelState) }
                }
            }
        }.asStateFlow()

    private fun createUiState(state: ViewModelState): AdminAccountFeedNewScreenUiState {
        val busy = state.fetching || state.saving
        return AdminAccountFeedNewScreenUiState(
            acct = "@$username",
            url = state.url,
            urlInputEnabled = !busy,
            fetching = state.fetching,
            fetchButtonEnabled = !busy && state.url.isNotBlank(),
            saving = state.saving,
            saveButtonEnabled = !busy && state.preview != null,
            closeEnabled = !state.saving,
            preview = state.preview?.toUiState(),
            errorMessage = state.errorMessage,
            listener = listener,
        )
    }

    private fun close() {
        if (viewModelStateFlow.value.saving) return

        viewModelScope.launch {
            events.send { it.close() }
        }
    }

    private fun fetchPreview() {
        val state = viewModelStateFlow.value
        val url = state.url.trim()
        if (url.isEmpty() || state.fetching || state.saving) return

        previewJob?.cancel()
        viewModelStateFlow.update { it.copy(fetching = true, preview = null, errorMessage = null) }

        previewJob = viewModelScope.launch {
            try {
                when (val result = api.previewFeed(url)) {
                    is AdminFeedPreviewResult.Success -> {
                        viewModelStateFlow.update {
                            it.copy(fetching = false, preview = result.preview, errorMessage = null)
                        }
                    }

                    is AdminFeedPreviewResult.Rejected -> {
                        viewModelStateFlow.update {
                            it.copy(fetching = false, preview = null, errorMessage = result.reason.toMessage())
                        }
                    }

                    is AdminFeedPreviewResult.Failure -> {
                        viewModelStateFlow.update {
                            it.copy(fetching = false, preview = null, errorMessage = result.message)
                        }
                    }
                }
            } finally {
                if (!isActive) {
                    viewModelStateFlow.update { it.copy(fetching = false) }
                }
            }
        }
    }

    /**
     * どのアカウントに付けるかはここで引く。id は表示に使わないので画面が持たず、
     * URL にも載せない。押した時に 1 回引くだけなら、開いた時点で待たせずに済む
     */
    private fun saveAndClose() {
        val state = viewModelStateFlow.value
        val url = state.url.trim()
        if (url.isEmpty() || state.preview == null || state.fetching || state.saving) return

        saveJob?.cancel()
        viewModelStateFlow.update { it.copy(saving = true, errorMessage = null) }

        saveJob = viewModelScope.launch {
            try {
                val accountId = when (val account = api.account(username)) {
                    is AdminAccountResult.Success -> account.account?.account?.id

                    is AdminAccountResult.Failure -> {
                        saveFailed(account.message)
                        return@launch
                    }
                }
                if (accountId == null) {
                    saveFailed("このアカウントは無い")
                    return@launch
                }

                when (val result = api.saveFeed(accountId = accountId, url = url)) {
                    is AdminSaveFeedResult.Success -> {
                        AdminFeedUpdates.notifyRegistered(username)
                        events.send { it.close() }
                    }

                    is AdminSaveFeedResult.Rejected -> saveFailed(result.reason.toMessage())

                    is AdminSaveFeedResult.Failure -> saveFailed(result.message)
                }
            } finally {
                if (!isActive) {
                    viewModelStateFlow.update { it.copy(saving = false) }
                }
            }
        }
    }

    private fun saveFailed(message: String) {
        viewModelStateFlow.update { it.copy(saving = false, errorMessage = message) }
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

    private fun AdminFeedPreview.toUiState(): AdminAccountFeedNewScreenUiState.Preview =
        AdminAccountFeedNewScreenUiState.Preview(
            title = title,
            siteUrl = siteUrl,
            format = format,
            description = description,
            itemCount = itemCount,
            sampleItems = sampleItems.map { item ->
                AdminAccountFeedNewScreenUiState.PreviewItem(
                    title = item.title,
                    link = item.link,
                    publishedAt = item.publishedAt?.let { UnixTimeUtil.format(it) },
                )
            },
        )

    private data class ViewModelState(
        val url: String = "",
        val fetching: Boolean = false,
        val preview: AdminFeedPreview? = null,
        val saving: Boolean = false,
        val errorMessage: String? = null,
    )

    interface Event {
        /**
         * 1 画面として積んでいるので、戻るのと同じ
         */
        suspend fun close()
    }
}
