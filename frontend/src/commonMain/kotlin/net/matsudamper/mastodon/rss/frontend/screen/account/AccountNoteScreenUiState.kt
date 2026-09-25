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

        /**
         * @param linkPreviews 本文のリンクごとの OGP。取れるまでは URL だけで埋めておく
         */
        data class Loaded(
            val account: Account,
            val contentHtml: String,
            val publishedAt: String,
            val favouriteCount: String?,
            val linkPreviews: List<LinkPreview>,
        ) : Content

        data class Error(
            val message: String,
        ) : Content
    }

    data class Account(
        val username: String,
        val displayName: String,
        val acct: String,
        val iconUrl: String?,
    )

    data class LinkPreview(
        val url: String,
        val title: String,
        val siteName: String,
        val imageUrl: String?,
    )

    @Immutable
    interface Listener {
        fun onClickClose()

        fun onClickReload()

        fun onClickAccount()

        fun onClickActivityPub()

        fun onClickLinkPreview(url: String)
    }
}
