package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.runtime.Immutable

data class AdminAccountsScreenUiState(
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        /**
         * ログインしていない。管理画面のトップに送る
         */
        data object RequireLogin : Content

        data class Loaded(
            val accounts: List<Account>,
            val hasMore: Boolean,
            val isLoadingMore: Boolean,
            val loadMoreErrorMessage: String?,
        ) : Content

        data class Error(
            val message: String,
        ) : Content
    }

    /**
     * 一覧の 1 行。
     *
     * @param acct 行の見出しとして大きく出す
     * @param actorUrl 見出しの下に小さく出す
     * @param createdAt 「追加: <値>」の形で出す。null ならこの行を出さない
     * @param username 行から開く画面のリンク先。文字としては出さない
     */
    data class Account(
        val username: String,
        val acct: String,
        val actorUrl: String,
        val createdAt: String?,
        val followerCount: Int,
    )

    @Immutable
    interface Listener {
        fun onClickReload()
        fun onClickLoadMore()
    }
}
