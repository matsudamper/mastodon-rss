package net.matsudamper.mastodon.rss.frontend.screen.account

import androidx.compose.runtime.Immutable
import net.matsudamper.mastodon.rss.frontend.ui.PublicScaffoldListener

data class AccountScreenUiState(
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        /**
         * この名前のアカウントは無い。見つからない画面を出す
         */
        data object NotFound : Content

        data class Loaded(
            val account: AccountUiState,
            val notes: List<NoteUiState>,
            val notesError: String?,
            val notesLoading: Boolean,
            val canLoadMore: Boolean,
            val loadingMore: Boolean,
        ) : Content

        data class Error(
            val message: String,
        ) : Content
    }

    @Immutable
    interface Listener : PublicScaffoldListener {
        fun onClickOperator()

        fun onClickReload()

        fun onClickReloadNotes()

        fun onClickLoadMore()

        fun onClickCopyAcct()
    }
}
