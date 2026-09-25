package net.matsudamper.mastodon.rss.frontend.screen.account

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.mastodon.rss.frontend.event.EventSender
import net.matsudamper.mastodon.rss.frontend.format.UnixTimeUtil
import net.matsudamper.mastodon.rss.frontend.logic.account.AccountApi
import net.matsudamper.mastodon.rss.frontend.logic.account.AccountNoteResult
import net.matsudamper.mastodon.rss.frontend.logic.account.NoteLinkPreview
import net.matsudamper.mastodon.rss.frontend.logic.account.NoteLinkPreviewsResult

class AccountNoteScreenViewModel(
    private val username: String,
    private val noteId: String,
    private val viewModelScope: CoroutineScope,
    private val api: AccountApi = AccountApi(),
) {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()

    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState())

    private var noteJob: Job? = null

    // uiStateFlow より後ろに置くと、初期値を組み立てる時点でまだ入っていない
    private val listener = object : AccountNoteScreenUiState.Listener {
        override fun onClickClose() {
            viewModelScope.launch { events.send { it.close() } }
        }

        override fun onClickReload() {
            reload()
        }

        override fun onClickActivityPub() {
            val note = (viewModelStateFlow.value.note as? AccountNoteResult.Success)?.note ?: return
            viewModelScope.launch { events.send { it.openExternalLink(note.url) } }
        }

        override fun onClickLinkPreview(url: String) {
            viewModelScope.launch { events.send { it.openExternalLink(url) } }
        }
    }

    val uiStateFlow: StateFlow<AccountNoteScreenUiState> =
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

    private fun reload() {
        noteJob?.cancel()
        viewModelStateFlow.update { it.copy(note = null, linkPreviews = listOf()) }
        noteJob = viewModelScope.launch {
            val result = api.note(username = username, id = noteId)
            viewModelStateFlow.update { it.copy(note = result) }
            if (result is AccountNoteResult.Success && result.linkUrls.isNotEmpty()) {
                loadLinkPreviews()
            }
        }
    }

    /**
     * 失敗しても取り直さず、OGP が無いものとして出す
     */
    private suspend fun loadLinkPreviews() {
        when (val result = api.linkPreviews(username = username, id = noteId)) {
            is NoteLinkPreviewsResult.Success -> {
                viewModelStateFlow.update { it.copy(linkPreviews = result.previews) }
            }

            is NoteLinkPreviewsResult.Failure -> Unit
        }
    }

    private fun createUiState(state: ViewModelState): AccountNoteScreenUiState {
        val content = when (val result = state.note) {
            null -> AccountNoteScreenUiState.Content.Loading

            AccountNoteResult.NotFound -> AccountNoteScreenUiState.Content.NotFound

            is AccountNoteResult.Failure -> AccountNoteScreenUiState.Content.Error(result.message)

            is AccountNoteResult.Success -> AccountNoteScreenUiState.Content.Loaded(
                contentHtml = result.note.contentHtml,
                publishedAt = UnixTimeUtil.format(result.note.publishedAt.epochSeconds),
                favouriteCount = result.note.favouriteCount.takeIf { it > 0 }?.toString(),
                linkPreviews = result.linkUrls.map { url ->
                    createLinkPreview(url = url, preview = state.linkPreviews.firstOrNull { it.url == url })
                },
            )
        }

        return AccountNoteScreenUiState(
            content = content,
            listener = listener,
        )
    }

    /**
     * @param preview まだ取れていなければ null。枠は先に出しておき、取れたら中身だけ差し替える
     */
    private fun createLinkPreview(
        url: String,
        preview: NoteLinkPreview?,
    ): AccountNoteScreenUiState.LinkPreview {
        return AccountNoteScreenUiState.LinkPreview(
            url = url,
            title = preview?.title ?: url,
            siteName = preview?.siteName ?: url.substringAfter("://").substringBefore('/'),
            imageUrl = preview?.imageUrl,
        )
    }

    private data class ViewModelState(
        val note: AccountNoteResult? = null,
        val linkPreviews: List<NoteLinkPreview> = listOf(),
    )

    interface Event {
        /**
         * 1 画面として積んでいるので、戻るのと同じ
         */
        suspend fun close()

        suspend fun openExternalLink(url: String)
    }
}
