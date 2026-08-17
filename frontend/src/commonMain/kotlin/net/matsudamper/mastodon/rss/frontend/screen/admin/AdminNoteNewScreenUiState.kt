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
         * 投稿の入力欄と、そのアカウントが配信した投稿の一覧。
         *
         * @param accounts どのアカウントとして投稿するかの選択肢。1 つでも選ばせる。
         *   どのアカウントから流れるかは相手のタイムラインでの見え方そのものなので、
         *   暗黙に決まっていると取り違えに気付けない
         * @param submitting true の間は入力欄とボタンを押せなくする
         * @param result 直前の投稿の結果。次の入力を始めたら消す
         * @param error 入力欄の下に赤字で出す
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
             * さらに古い投稿があるか。押すと続きを足す
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
     * 配信した投稿 1 件。
     *
     * @param text 本文からタグを外したもの。管理画面で HTML をそのまま描くと、
     *   配信した中身と画面の見え方がずれる
     */
    data class Note(
        val url: String,
        val text: String,
    )

    /**
     * 投稿の結果として画面に出すもの。
     *
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
