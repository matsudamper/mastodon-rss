package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.runtime.Immutable
import net.matsudamper.mastodon.rss.frontend.ui.AdminScaffoldListener

data class AdminDeliveriesScreenUiState(
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
         * @param emptyText 1 件も無いことを伝える一行。1 件でもあれば null
         * @param loadMoreButtonText 続きを取るボタンの文字。取り損ねた後は誘い方が変わる
         */
        data class Loaded(
            val deliveries: List<Delivery>,
            val emptyText: String?,
            val loadMoreVisible: Boolean,
            val loadingMore: Boolean,
            val loadMoreErrorMessage: String?,
            val loadMoreButtonText: String,
        ) : Content

        data class Error(
            val message: String,
        ) : Content
    }

    /**
     * 一覧の 1 行。
     *
     * @param kindText 何を送る配信か
     * @param acct どのアカウントとして送るか
     * @param statusText 送っている最中か、次にいつ送るか
     * @param attemptsText これまでに送ろうとした回数
     */
    data class Delivery(
        val kindText: String,
        val acct: String,
        val inbox: String,
        val statusText: String,
        val attemptsText: String,
        val lastError: String?,
    )

    @Immutable
    interface Listener : AdminScaffoldListener {
        fun onClickReload()

        fun onClickLoadMore()
    }
}
