package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.runtime.Immutable

data class AdminNoteNewScreenUiState(
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
         * @param accounts どのアカウントとして投稿するかの選択肢。1 つでも選ばせる
         * @param submitting true の間は入力欄とボタンを押せなくする
         * @param result 直前の投稿の結果。次の入力を始めたら消す
         */
        data class Input(
            val accounts: List<Account>,
            val selectedUsername: String,
            val body: String,
            val submitting: Boolean,
            val result: PostResult?,
            val error: String?,
            val notes: List<Note>,
            /**
             * さらに古い投稿があるか
             */
            val canLoadMore: Boolean,
            val loadingMore: Boolean,
        ) : Content {
            val canSubmit: Boolean get() = !submitting && body.isNotBlank() && selectedUsername.isNotEmpty()
        }

        data class Error(
            val message: String,
        ) : Content
    }

    /**
     * @param acct 選択肢に出す表示。`@name@domain` の形
     */
    data class Account(
        val username: String,
        val acct: String,
    )

    /**
     * @param text 本文からタグを外したもの
     */
    data class Note(
        val url: String,
        val text: String,
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

        fun onAccountSelected(username: String)

        fun onClickPost()

        fun onClickLoadMore()
    }
}
