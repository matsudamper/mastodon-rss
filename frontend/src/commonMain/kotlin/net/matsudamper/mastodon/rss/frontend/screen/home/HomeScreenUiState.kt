package net.matsudamper.mastodon.rss.frontend.screen.home

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
        ) : Content
    }

    data class Account(
        val username: String,
        val acct: String,
        val actorUrl: String,
    )

    interface Listener {
        fun onClickReload()
        fun onClickLoadMore()
    }
}
