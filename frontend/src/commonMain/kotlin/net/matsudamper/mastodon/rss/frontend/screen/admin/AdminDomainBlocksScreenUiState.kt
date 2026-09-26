package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.runtime.Immutable
import net.matsudamper.mastodon.rss.frontend.ui.AdminScaffoldListener

/**
 * @param editor 追加・編集のダイアログ。出していなければ null
 */
data class AdminDomainBlocksScreenUiState(
    val content: Content,
    val editor: Editor?,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        /**
         * ログインしていない。管理画面のトップに送る
         */
        data object RequireLogin : Content

        /**
         * @param emptyText 1 件も無いことを伝える一行。1 件でもあれば null
         * @param loadMoreVisible 末尾に続きの枠を出す
         * @param loadMoreOnVisible 枠が見えたら続きを取りに行く。取っている間と失敗した後は false
         * @param loadMoreErrorMessage 続きが取れなかったときの文言。押して再試行するボタンと一緒に出す
         */
        data class Loaded(
            val blocks: List<Block>,
            val emptyText: String?,
            val loadMoreVisible: Boolean,
            val loadMoreOnVisible: Boolean,
            val loadMoreErrorMessage: String?,
        ) : Content

        data class Error(
            val message: String,
        ) : Content
    }

    /**
     * 一覧の 1 行。押すと編集のダイアログが開く
     *
     * @param reasonText 誰が止めたか
     * @param targetText 何を止めているか
     * @param createdAtText いつから止めているか
     */
    data class Block(
        val domain: String,
        val reasonText: String,
        val targetText: String,
        val description: String?,
        val createdAtText: String,
        val listener: BlockListener,
    )

    @Immutable
    interface BlockListener {
        fun onClick()
    }

    /**
     * @param noticeText 保存すると何が変わるかの注意。無ければ null
     * @param deleteButtonVisible 止めているものを開いたときだけ外すボタンを出す
     */
    data class Editor(
        val title: String,
        val domain: String,
        val domainInputEnabled: Boolean,
        val blockDelivery: Boolean,
        val blockInbox: Boolean,
        val reasonDescription: String,
        val noticeText: String?,
        val inputEnabled: Boolean,
        val saveButtonText: String,
        val saveButtonEnabled: Boolean,
        val deleteButtonVisible: Boolean,
        val deleteButtonText: String,
        val deleteButtonEnabled: Boolean,
        val closeEnabled: Boolean,
        val errorMessage: String?,
        val listener: EditorListener,
    )

    @Immutable
    interface EditorListener {
        fun onDomainChanged(text: String)

        fun onBlockDeliveryChanged(checked: Boolean)

        fun onBlockInboxChanged(checked: Boolean)

        fun onReasonDescriptionChanged(text: String)

        fun onClickSave()

        fun onClickDelete()

        fun onClickClose()
    }

    @Immutable
    interface Listener : AdminScaffoldListener {
        fun onClickReload()

        fun onClickAdd()

        fun onLoadMore()
    }
}
