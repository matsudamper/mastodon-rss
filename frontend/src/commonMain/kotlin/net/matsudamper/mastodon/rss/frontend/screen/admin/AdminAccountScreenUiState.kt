package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.runtime.Immutable

data class AdminAccountScreenUiState(
    val acct: String,
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        /**
         * ログインしていない。管理画面のトップに送る
         */
        data object RequireLogin : Content

        /**
         * その名前のアカウントが無い
         */
        data object NotFound : Content

        data class Loaded(
            val account: Account,
        ) : Content

        data class Error(
            val message: String,
        ) : Content
    }

    // TODO: フォロワー数は Phase 3 の永続化実装後にここへ足す
    // TODO: 投稿の入力欄と、配信した投稿の一覧は Phase 4 でここに足す

    /**
     * @param acct Mastodon の検索窓に貼る形
     * @param createdAt 「追加: <値>」の形で出す。null ならこの行を出さない
     */
    data class Account(
        val username: String,
        val acct: String,
        val actorUrl: String,
        val createdAt: String?,
    )

    @Immutable
    interface Listener {
        fun onClickReload()
    }
}
