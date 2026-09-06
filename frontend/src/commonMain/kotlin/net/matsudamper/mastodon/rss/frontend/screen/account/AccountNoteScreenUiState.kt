package net.matsudamper.mastodon.rss.frontend.screen.account

import androidx.compose.runtime.Immutable

data class AccountNoteScreenUiState(
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        /**
         * この投稿は無い。消された投稿の URL を開いたときにここに来る
         */
        data object NotFound : Content

        data class Loaded(
            val contentHtml: String,
            val publishedAt: String,
        ) : Content

        data class Error(
            val message: String,
        ) : Content
    }

    @Immutable
    interface Listener {
        fun onClickClose()

        fun onClickReload()

        fun onClickActivityPub()
    }
}
