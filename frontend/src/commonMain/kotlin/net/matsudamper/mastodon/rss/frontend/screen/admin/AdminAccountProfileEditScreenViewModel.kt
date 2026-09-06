package net.matsudamper.mastodon.rss.frontend.screen.admin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.mastodon.rss.frontend.event.EventSender
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminAccountResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminAccountUpdates
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminApi
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminFeedPreviewResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminUpdateAccountProfileResult
import net.matsudamper.mastodon.rss.shared.AccountProfileLimits

class AdminAccountProfileEditScreenViewModel(
    private val username: String,
    private val viewModelScope: CoroutineScope,
    private val api: AdminApi,
) {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val state = MutableStateFlow(ViewModelState())
    private val listener = object : AdminAccountProfileEditScreenUiState.Listener {
        override fun onDisplayNameChanged(text: String) = state.update { it.copy(displayName = text, errorMessage = null) }
        override fun onSummaryChanged(text: String) = state.update { it.copy(summary = text, errorMessage = null) }
        override fun onClickSave() = save()
        override fun onClickApplyFeed() = applyFeed()
        override fun onClickClose() = close()
    }
    val uiStateFlow: StateFlow<AdminAccountProfileEditScreenUiState> = MutableStateFlow(createUiState(state.value)).also { ui ->
        viewModelScope.launch { state.collect { value -> ui.value = createUiState(value) } }
    }.asStateFlow()

    init {
        viewModelScope.launch {
            when (val account = api.watchAccount(username).first()) {
                is AdminAccountResult.Success -> state.update {
                    it.copy(
                        loaded = account.account != null,
                        displayName = account.account?.account?.displayName.orEmpty(),
                        summary = account.account?.account?.summary.orEmpty(),
                        feedUrl = account.account?.feed?.url,
                        errorMessage = if (account.account == null) "このアカウントは無い" else null,
                    )
                }

                is AdminAccountResult.Failure -> state.update { it.copy(errorMessage = account.message) }
            }
        }
    }

    private fun createUiState(value: ViewModelState) = AdminAccountProfileEditScreenUiState(
        displayName = value.displayName,
        summary = value.summary,
        inputEnabled = value.loaded && !value.saving && !value.applyingFeed,
        saving = value.saving,
        applyingFeed = value.applyingFeed,
        applyFeedButtonEnabled = value.loaded && value.feedUrl != null && !value.saving && !value.applyingFeed,
        saveButtonEnabled = value.loaded && !value.saving && !value.applyingFeed,
        closeEnabled = !value.saving && !value.applyingFeed,
        errorMessage = value.errorMessage,
        listener = listener,
    )

    private fun save() {
        if (!state.value.loaded || state.value.saving || state.value.applyingFeed) return
        state.update { it.copy(saving = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = api.updateAccountProfile(username, state.value.displayName, state.value.summary)) {
                is AdminUpdateAccountProfileResult.Success -> {
                    AdminAccountUpdates.notifyChanged(username)
                    events.send { it.close() }
                }

                is AdminUpdateAccountProfileResult.Rejected -> state.update { it.copy(saving = false, errorMessage = result.toMessage()) }

                is AdminUpdateAccountProfileResult.Failure -> state.update { it.copy(saving = false, errorMessage = result.message) }
            }
        }
    }

    private fun applyFeed() {
        val feedUrl = state.value.feedUrl ?: return
        if (!state.value.loaded || state.value.saving || state.value.applyingFeed) return
        state.update { it.copy(applyingFeed = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = api.previewFeed(feedUrl)) {
                // 一覧用に切り詰めた description ではなく配信元が書いたままの説明を入れる。
                // フィードの文量は入力上限と関係なく決まるので、保存が拒まれないように上限で切る
                is AdminFeedPreviewResult.Success -> state.update {
                    it.copy(
                        displayName = result.preview.title.orEmpty().truncateToLimit(AccountProfileLimits.DISPLAY_NAME_MAX_LENGTH),
                        summary = result.preview.fullDescription.orEmpty().truncateToLimit(AccountProfileLimits.SUMMARY_MAX_LENGTH),
                        applyingFeed = false,
                    )
                }

                is AdminFeedPreviewResult.Rejected -> state.update {
                    it.copy(applyingFeed = false, errorMessage = "フィードを取得できなかった")
                }

                is AdminFeedPreviewResult.Failure -> state.update {
                    it.copy(applyingFeed = false, errorMessage = result.message)
                }
            }
        }
    }

    /**
     * 上限のコードポイント数までを残す。
     *
     * サロゲートペアの途中で切ると壊れた文字になるので、ペアは 1 文字として数える。
     */
    private fun String.truncateToLimit(maxLength: Int): String {
        var count = 0
        var index = 0
        while (index < length) {
            if (count == maxLength) return substring(0, index)
            val surrogatePair = this[index].isHighSurrogate() &&
                index + 1 < length &&
                this[index + 1].isLowSurrogate()
            index += if (surrogatePair) 2 else 1
            count += 1
        }
        return this
    }

    private fun close() {
        if (!state.value.saving && !state.value.applyingFeed) viewModelScope.launch { events.send { it.close() } }
    }

    private fun AdminUpdateAccountProfileResult.Rejected.toMessage(): String = when {
        unknownAccount -> "このアカウントは無い"
        displayNameMaxLength != null -> "表示名は $displayNameMaxLength 文字まで"
        summaryMaxLength != null -> "説明文は $summaryMaxLength 文字まで"
        else -> "保存できなかった"
    }

    private data class ViewModelState(
        val loaded: Boolean = false,
        val displayName: String = "",
        val summary: String = "",
        val saving: Boolean = false,
        val applyingFeed: Boolean = false,
        val feedUrl: String? = null,
        val errorMessage: String? = null,
    )

    interface Event {
        suspend fun close()
    }
}
