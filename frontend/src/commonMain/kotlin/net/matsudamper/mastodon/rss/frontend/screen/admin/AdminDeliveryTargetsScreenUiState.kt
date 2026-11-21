package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.runtime.Immutable
import net.matsudamper.mastodon.rss.frontend.ui.AdminScaffoldListener

data class AdminDeliveryTargetsScreenUiState(
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        /**
         * ログインしていない。管理画面のトップに送る
         */
        data object RequireLogin : Content

        /**
         * @param currentLimitText いま効いている上限
         * @param limitInput 上限の入力欄の中身
         * @param saveMessage 保存できたときの一行。保存前と入力を変えた後は null
         * @param saveErrorMessage 保存できなかったときの一行
         * @param hostsDescription ホストの一覧が何を並べたものか
         * @param hostsEmptyText 1 件も無いことを伝える一行。1 件でもあれば null
         */
        data class Loaded(
            val currentLimitText: String,
            val limitInput: String,
            val limitInputEnabled: Boolean,
            val saveButtonText: String,
            val saveButtonEnabled: Boolean,
            val saveMessage: String?,
            val saveErrorMessage: String?,
            val hostsDescription: String,
            val hosts: List<Host>,
            val hostsEmptyText: String?,
        ) : Content

        data class Error(
            val message: String,
        ) : Content
    }

    /**
     * ホストの一覧の 1 行
     *
     * @param countText 宛先の数と上限
     * @param limitReachedText 上限に届いているときの一行。届いていなければ null
     */
    data class Host(
        val host: String,
        val countText: String,
        val countTextHighlighted: Boolean,
        val limitReachedText: String?,
    )

    @Immutable
    interface Listener : AdminScaffoldListener {
        fun onClickReload()

        fun onLimitInputChanged(text: String)

        fun onClickSave()
    }
}
