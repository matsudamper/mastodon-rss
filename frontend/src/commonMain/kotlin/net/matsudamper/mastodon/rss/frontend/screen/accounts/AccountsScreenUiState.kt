package net.matsudamper.mastodon.rss.frontend.screen.accounts

import net.matsudamper.mastodon.rss.frontend.ui.PublicScaffoldListener

data class AccountsScreenUiState(
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        data class Error(val message: String) : Content

        /**
         * @param loadMoreVisible 末尾に続きの枠を出す
         * @param loadMoreOnVisible 枠が見えたら続きを取りに行く。取っている間と失敗した後は false
         * @param loadMoreErrorMessage 続きが取れなかったときの文言。押して再試行するボタンと一緒に出す
         */
        data class Loaded(
            val accounts: List<Account>,
            val loadMoreVisible: Boolean,
            val loadMoreOnVisible: Boolean,
            val loadMoreErrorMessage: String?,
        ) : Content
    }

    data class Account(
        val username: String,
        val acct: String,
        val displayName: String,
        val iconUrl: String?,
    )

    interface Listener : PublicScaffoldListener {
        fun onClickReload()

        fun onLoadMore()

        fun onClickAccount(username: String)
    }
}
