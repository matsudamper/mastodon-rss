package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.runtime.Immutable
import net.matsudamper.mastodon.rss.frontend.ui.AdminScaffoldListener

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
     * @param displayName 行の見出しとして大きく出す。未設定なら空文字
     * @param acct DisplayName の下に出す
     * @param actorUrl Actor URL として出す
     * @param iconUrl アイコン画像。無ければ null
     * @param createdAt 「追加: <値>」の形で出す
     * @param username 行から開く画面のリンク先。DisplayName 未設定時は見出しにも使う
     */
    data class Account(
        val username: String,
        val displayName: String,
        val acct: String,
        val actorUrl: String,
        val iconUrl: String?,
        val createdAt: String,
        val followerCount: Int,
    )

    @Immutable
    interface Listener : AdminScaffoldListener {
        fun onClickNewAccount()

        fun onClickPublic(username: String)

        fun onClickAccount(username: String)

        fun onClickReload()

        fun onClickLoadMore()
    }
}
