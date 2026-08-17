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

        /**
         * @param account この画面が扱うアカウント
         * @param post 投稿の入力欄
         * @param notes 配信した投稿。新しい順
         * @param canLoadMore さらに古い投稿があるか
         */
        data class Loaded(
            val account: Account,
            val post: Post,
            val notes: List<Note>,
            val canLoadMore: Boolean,
            val loadingMore: Boolean,
        ) : Content

        data class Error(
            val message: String,
        ) : Content
    }

    /**
     * @param acct Mastodon の検索窓に貼る形
     * @param createdAt 「追加: <値>」の形で出す。null ならこの行を出さない
     */
    data class Account(
        val username: String,
        val acct: String,
        val actorUrl: String,
        val createdAt: String?,
        val followerCount: Int,
    )

    /**
     * @param submitting true の間は入力欄とボタンを押せなくする
     * @param result 直前の投稿の結果。次の入力を始めたら消す
     */
    data class Post(
        val body: String,
        val submitting: Boolean,
        val result: PostResult?,
        val error: String?,
    ) {
        val canSubmit: Boolean get() = !submitting && body.isNotBlank()
    }

    /**
     * @param text 本文からタグを外したもの
     */
    data class Note(
        val url: String,
        val text: String,
        val publishedAt: String,
    )

    /**
     * @param targets 送った宛先の数
     * @param delivered そのうち届いた数
     */
    data class PostResult(
        val url: String,
        val targets: Int,
        val delivered: Int,
    )

    @Immutable
    interface Listener {
        fun onBodyChanged(text: String)

        fun onClickPost()

        fun onClickLoadMore()

        fun onClickReload()
    }
}
