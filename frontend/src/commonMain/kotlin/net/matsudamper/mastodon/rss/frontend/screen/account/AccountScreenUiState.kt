package net.matsudamper.mastodon.rss.frontend.screen.account

import androidx.compose.runtime.Immutable

data class AccountScreenUiState(
    val content: Content,
    val noteDialog: NoteDialogUiState?,
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
    interface Listener {
        fun onClickReload()

        fun onClickReloadNotes()

        fun onClickLoadMore()

        fun onClickCopyAcct()

        fun onClickReloadNote()
    }
}

sealed interface NoteDialogUiState {
    data object Loading : NoteDialogUiState

    data object NotFound : NoteDialogUiState

    data class Loaded(
        val contentHtml: String,
        val publishedAt: String,
        val activityPubUrl: String,
    ) : NoteDialogUiState

    data class Error(
        val message: String,
    ) : NoteDialogUiState
}
