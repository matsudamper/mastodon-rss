package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.runtime.Immutable
import net.matsudamper.mastodon.rss.frontend.ui.AdminScaffoldListener

data class AdminAccountNewScreenUiState(
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
         * 入力欄と追加ボタン。
         *
         * @param submitting true の間は入力欄とボタンを押せなくし、ボタンの文字を待ち状態にする
         * @param error 入力欄の下に赤字で出す。null なら何も出さない
         */
        data class Input(
            val username: String,
            val submitting: Boolean,
            val error: String?,
        ) : Content {
            val canSubmit: Boolean get() = !submitting && username.isNotBlank()
        }

        /**
         * 追加できた。入力欄の代わりに出す。
         *
         * @param acct 追加した名前として文中に出す
         */
        data class Added(
            val acct: String,
        ) : Content

        data class Error(
            val message: String,
        ) : Content
    }

    @Immutable
    interface Listener : AdminScaffoldListener {
        fun onUsernameChanged(text: String)

        fun onClickAdd()

        fun onClickAddAnother()

        fun onClickAccounts()
    }
}
