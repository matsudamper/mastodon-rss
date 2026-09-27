package net.matsudamper.mastodon.rss.frontend.screen.admin

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
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminApi
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminDeleteDomainBlockResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminDomainBlock
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminDomainBlockReason
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminDomainBlocksResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminSaveDomainBlockResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminSessionResult
import net.matsudamper.mastodon.rss.frontend.navigation.Screen

class AdminDomainBlocksScreenViewModel(
    private val viewModelScope: CoroutineScope,
    private val api: AdminApi,
) {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState())
    private val blocksPaging = api.domainBlocks(limit = PAGE_SIZE)
    private var sessionJob: Job? = null
    private var blocksJob: Job? = null
    private var loadMoreJob: Job? = null

    private val editorListener = object : AdminDomainBlocksScreenUiState.EditorListener {
        override fun onDomainChanged(text: String) {
            updateEditor { it.copy(domain = text, errorMessage = null) }
        }

        override fun onBlockDeliveryChanged(checked: Boolean) {
            updateEditor { it.copy(blockDelivery = checked, errorMessage = null) }
        }

        override fun onBlockInboxChanged(checked: Boolean) {
            updateEditor { it.copy(blockInbox = checked, errorMessage = null) }
        }

        override fun onReasonDescriptionChanged(text: String) {
            updateEditor { it.copy(reasonDescription = text, errorMessage = null) }
        }

        override fun onClickSave() {
            save()
        }

        override fun onClickDelete() {
            delete()
        }

        override fun onClickClose() {
            val editor = viewModelStateFlow.value.editor ?: return
            if (editor.busy) return
            viewModelStateFlow.update { it.copy(editor = null) }
        }
    }

    val uiStateFlow: StateFlow<AdminDomainBlocksScreenUiState> =
        MutableStateFlow(
            AdminDomainBlocksScreenUiState(
                content = AdminDomainBlocksScreenUiState.Content.Loading,
                editor = null,
                listener = object : AdminDomainBlocksScreenUiState.Listener {
                    override fun onClickHome() {
                        navigate(Screen.Home)
                    }

                    override fun onClickAdmin() {
                        navigate(Screen.Admin)
                    }

                    override fun onClickReload() {
                        reload()
                    }

                    override fun onClickAdd() {
                        openEditor(original = null)
                    }

                    override fun onLoadMore() {
                        loadMore()
                    }
                },
            ),
        ).also { uiStateFlow ->
            viewModelScope.launch {
                viewModelStateFlow.collect { viewModelState ->
                    uiStateFlow.update { uiState ->
                        uiState.copy(
                            content = createContent(viewModelState),
                            editor = viewModelState.editor?.let { createEditor(it) },
                        )
                    }
                }
            }
        }.asStateFlow()

    fun onStart() {
        reload()
    }

    private fun navigate(screen: Screen) {
        viewModelScope.launch {
            events.send { it.navigate(screen) }
        }
    }

    private fun reload() {
        sessionJob?.cancel()
        blocksJob?.cancel()
        loadMoreJob?.cancel()
        blocksJob = null
        viewModelStateFlow.update { ViewModelState() }
        sessionJob = viewModelScope.launch {
            api.session().collect { session ->
                viewModelStateFlow.update { it.copy(session = session) }

                if (session is AdminSessionResult.Success && session.loggedIn) {
                    if (blocksJob == null) watchBlocks()
                } else {
                    blocksJob?.cancel()
                    loadMoreJob?.cancel()
                    blocksJob = null
                    viewModelStateFlow.update {
                        it.copy(blocks = null, loadingMore = false, loadMoreErrorMessage = null, editor = null)
                    }
                }
            }
        }
    }

    /**
     * 一覧は先頭のページを watch して受け取る。続きを足したときもここに流れてくる。
     *
     * 追加と変更の後も、ここで先頭から取り直す。追加したものがどのページに入るかは
     * ドメインの並びで決まるので、手元の一覧に足すだけでは位置が合わない
     */
    private fun watchBlocks() {
        blocksJob?.cancel()
        loadMoreJob?.cancel()
        viewModelStateFlow.update { it.copy(loadingMore = false, loadMoreErrorMessage = null) }
        blocksJob = viewModelScope.launch {
            blocksPaging.watch().collect { blocks ->
                viewModelStateFlow.update { it.copy(blocks = blocks) }
            }
        }
    }

    private fun loadMore() {
        val state = viewModelStateFlow.value
        val blocks = state.blocks as? AdminDomainBlocksResult.Success ?: return
        if (state.loadingMore) return
        val cursor = blocks.nextCursor ?: return

        viewModelStateFlow.update { it.copy(loadingMore = true, loadMoreErrorMessage = null) }

        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            when (val result = blocksPaging.loadMore(cursor)) {
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

    /**
     * @param original 開いたもの。追加なら null
     */
    private fun openEditor(original: AdminDomainBlock?) {
        viewModelStateFlow.update {
            it.copy(
                editor = EditorState(
                    original = original,
                    domain = original?.domain.orEmpty(),
                    // 手で止めるときは、何も選ばなくても全部止まる
                    blockDelivery = original?.blockDelivery ?: true,
                    blockInbox = original?.blockInbox ?: true,
                    reasonDescription = original?.reasonDescription.orEmpty(),
                    saving = false,
                    deleting = false,
                    errorMessage = null,
                ),
            )
        }
    }

    private fun updateEditor(block: (EditorState) -> EditorState) {
        viewModelStateFlow.update { state ->
            val editor = state.editor ?: return@update state
            if (editor.busy) return@update state
            state.copy(editor = block(editor))
        }
    }

    private fun save() {
        val editor = viewModelStateFlow.value.editor ?: return
        if (editor.busy) return
        viewModelStateFlow.update { it.copy(editor = editor.copy(saving = true, errorMessage = null)) }

        viewModelScope.launch {
            val result = api.saveDomainBlock(
                domain = editor.domain,
                blockDelivery = editor.blockDelivery,
                blockInbox = editor.blockInbox,
                reasonDescription = editor.reasonDescription,
            )
            when (result) {
                is AdminSaveDomainBlockResult.Success -> {
                    viewModelStateFlow.update { it.copy(editor = null) }
                    watchBlocks()
                }

                is AdminSaveDomainBlockResult.Rejected -> updateEditorResult(result.toMessage())

                is AdminSaveDomainBlockResult.Failure -> updateEditorResult(result.message)
            }
        }
    }

    private fun delete() {
        val editor = viewModelStateFlow.value.editor ?: return
        val original = editor.original ?: return
        if (editor.busy) return
        viewModelStateFlow.update { it.copy(editor = editor.copy(deleting = true, errorMessage = null)) }

        viewModelScope.launch {
            when (val result = api.deleteDomainBlock(original.domain)) {
                // 先に削除されていても、止めていない状態になったことは同じ
                AdminDeleteDomainBlockResult.Success,
                AdminDeleteDomainBlockResult.NotFound,
                -> {
                    viewModelStateFlow.update { it.copy(editor = null) }
                    watchBlocks()
                }

                is AdminDeleteDomainBlockResult.Failure -> updateEditorResult(result.message)
            }
        }
    }

    private fun updateEditorResult(errorMessage: String) {
        viewModelStateFlow.update { state ->
            val editor = state.editor ?: return@update state
            state.copy(editor = editor.copy(saving = false, deleting = false, errorMessage = errorMessage))
        }
    }

    private fun createContent(state: ViewModelState): AdminDomainBlocksScreenUiState.Content {
        val session = state.session ?: return AdminDomainBlocksScreenUiState.Content.Loading

        when (session) {
            is AdminSessionResult.Failure -> {
                return AdminDomainBlocksScreenUiState.Content.Error(session.message)
            }

            is AdminSessionResult.Success -> {
                if (!session.loggedIn) return AdminDomainBlocksScreenUiState.Content.RequireLogin
            }
        }

        return when (val blocks = state.blocks) {
            null -> AdminDomainBlocksScreenUiState.Content.Loading

            is AdminDomainBlocksResult.Failure -> AdminDomainBlocksScreenUiState.Content.Error(blocks.message)

            is AdminDomainBlocksResult.Success -> {
                AdminDomainBlocksScreenUiState.Content.Loaded(
                    blocks = blocks.blocks.map { block ->
                        AdminDomainBlocksScreenUiState.Block(
                            domain = block.domain,
                            reasonText = block.reason.toText(),
                            targetText = targetText(blockDelivery = block.blockDelivery, blockInbox = block.blockInbox),
                            description = block.reasonDescription,
                            createdAtText = "${UnixTimeUtil.format(block.createdAt)} から",
                            listener = object : AdminDomainBlocksScreenUiState.BlockListener {
                                override fun onClick() {
                                    openEditor(original = block)
                                }
                            },
                        )
                    },
                    emptyText = "止めているドメインは無い。".takeIf { blocks.blocks.isEmpty() },
                    loadMoreVisible = blocks.hasMore,
                    loadMoreOnVisible = blocks.hasMore && !state.loadingMore && state.loadMoreErrorMessage == null,
                    loadMoreErrorMessage = state.loadMoreErrorMessage,
                )
            }
        }
    }

    private fun createEditor(editor: EditorState): AdminDomainBlocksScreenUiState.Editor {
        val original = editor.original
        return AdminDomainBlocksScreenUiState.Editor(
            title = if (original == null) "ドメインを止める" else original.domain,
            domain = editor.domain,
            // ドメインを変えると別の行になる。変えたいときは削除してから追加し直す
            domainInputEnabled = original == null && !editor.busy,
            blockDelivery = editor.blockDelivery,
            blockInbox = editor.blockInbox,
            reasonDescription = editor.reasonDescription,
            noticeText = "届かなくなったので自動で止めたもの。保存すると手で止めたものに変わり、相手が戻ってきても自動では外れなくなる。"
                .takeIf { original?.reason == AdminDomainBlockReason.UNAVAILABLE },
            inputEnabled = !editor.busy,
            saveButtonText = if (editor.saving) "保存中" else "保存",
            // 何も止めない行を作ると、届かなくなっても自動で止められなくなる。やめるなら削除する
            saveButtonEnabled = !editor.busy && editor.domain.isNotBlank() && (editor.blockDelivery || editor.blockInbox),
            deleteButtonVisible = original != null,
            deleteButtonText = if (editor.deleting) "削除中" else "削除",
            deleteButtonEnabled = !editor.busy,
            closeEnabled = !editor.busy,
            errorMessage = editor.errorMessage,
            listener = editorListener,
        )
    }

    private fun targetText(
        blockDelivery: Boolean,
        blockInbox: Boolean,
    ): String =
        when {
            blockDelivery && blockInbox -> "配信と受信を止めている"
            blockDelivery -> "配信を止めている"
            blockInbox -> "受信を止めている"
            else -> "何も止めていない"
        }

    private fun AdminDomainBlockReason.toText(): String =
        when (this) {
            AdminDomainBlockReason.UNAVAILABLE -> "届かなくなったので自動で止めた"
            AdminDomainBlockReason.MANUAL -> "手で止めた"
            AdminDomainBlockReason.UNKNOWN -> "不明"
        }

    private fun AdminSaveDomainBlockResult.Rejected.toMessage(): String =
        when {
            invalidDomain -> "ドメインとして読めない"
            nothingBlocked -> "配信か受信のどちらかは止める。止めるのをやめるなら削除する"
            reasonDescriptionMaxLength != null -> "理由は $reasonDescriptionMaxLength 文字まで"
            else -> "保存できなかった"
        }

    private data class ViewModelState(
        val session: AdminSessionResult? = null,
        val blocks: AdminDomainBlocksResult? = null,
        val loadingMore: Boolean = false,
        val loadMoreErrorMessage: String? = null,
        val editor: EditorState? = null,
    )

    /**
     * @param original 開いたもの。追加なら null
     */
    private data class EditorState(
        val original: AdminDomainBlock?,
        val domain: String,
        val blockDelivery: Boolean,
        val blockInbox: Boolean,
        val reasonDescription: String,
        val saving: Boolean,
        val deleting: Boolean,
        val errorMessage: String?,
    ) {
        val busy: Boolean get() = saving || deleting
    }

    interface Event {
        suspend fun navigate(screen: Screen)
    }

    private companion object {
        /**
         * 1 回に取る件数。上限はサーバー側で決まる
         */
        const val PAGE_SIZE = 20
    }
}
