package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.runtime.Immutable

data class AdminAccountNewScreenUiState(
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        /** ログインしていない。管理画面のトップに送る */
        data object RequireLogin : Content

        data class Input(
            val username: String,
            val submitting: Boolean,
            val error: String?,
        ) : Content {
            val canSubmit: Boolean get() = !submitting && username.isNotBlank()
        }

        /** 追加できた。続けて追加できるよう、入力に戻す口も出す */
        data class Added(
            val acct: String,
        ) : Content

        data class Error(
            val message: String,
        ) : Content
    }

    @Immutable
    interface Listener {
        fun onUsernameChanged(text: String)

        fun onClickAdd()

        fun onClickAddAnother()
    }
}
