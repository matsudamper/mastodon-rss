package net.matsudamper.mastodon.rss.frontend.screen.home

import net.matsudamper.mastodon.rss.frontend.ui.PublicScaffoldListener

data class HomeScreenUiState(
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        data class Error(val message: String) : Content

        data class Loaded(
            val accounts: List<Account>,
            val hasMore: Boolean,
            val isLoadingMore: Boolean,
            val loadMoreErrorMessage: String?,
        ) : Content
    }

    data class Account(
        val username: String,
        val acct: String,
    )

    interface Listener : PublicScaffoldListener {
        fun onClickReload()

        fun onClickLoadMore()

        fun onClickAccount(username: String)
    }
}
