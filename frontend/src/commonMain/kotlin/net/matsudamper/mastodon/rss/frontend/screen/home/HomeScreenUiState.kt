package net.matsudamper.mastodon.rss.frontend.screen.home

import androidx.compose.runtime.Immutable
import net.matsudamper.mastodon.rss.frontend.ui.PublicScaffoldListener

data class HomeScreenUiState(
    val timeline: Timeline,
    val accounts: Accounts,
    val listener: Listener,
) {
    /**
     * 全アカウントの投稿を新しい順に並べたもの
     */
    sealed interface Timeline {
        data object Loading : Timeline

        data class Error(val message: String) : Timeline

        data class Loaded(
            val notes: List<Note>,
            val loadMoreVisible: Boolean,
            val loadingMore: Boolean,
            val loadMoreErrorMessage: String?,
        ) : Timeline
    }

    /**
     * トップに出すアカウント。全部ではなく先頭の一部だけ
     */
    sealed interface Accounts {
        data object Loading : Accounts

        data class Error(val message: String) : Accounts

        data class Loaded(
            val accounts: List<Account>,
        ) : Accounts
    }

    data class Note(
        val url: String,
        val contentHtml: String,
        val publishedAt: String,
        val account: Account,
        val listener: Listener,
    ) {
        @Immutable
        interface Listener {
            fun onClick()
        }
    }

    data class Account(
        val username: String,
        val acct: String,
        val displayName: String,
        val iconUrl: String?,
        val listener: Listener,
    ) {
        @Immutable
        interface Listener {
            fun onClick()
        }
    }

    @Immutable
    interface Listener : PublicScaffoldListener {
        fun onClickReloadTimeline()

        fun onClickLoadMore()

        fun onClickReloadAccounts()

        fun onClickAllAccounts()
    }
}
