package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.runtime.Immutable

data class AdminScreenUiState(
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        data class Login(
            val password: String,
            val submitting: Boolean,
            val error: String?,
        ) : Content

        data object LoggedIn : Content

       // ログインが無効化されている
        data object NotConfigured : Content

        /** 入力欄を出すと、パスワードの問題だと思って何度も試すことになる */
        data class Unavailable(
            val message: String,
        ) : Content
    }

    @Immutable
    interface Listener {
        fun onPasswordChanged(text: String)

        fun onClickLogin()

        fun onClickLogout()

        fun onClickRetry()
    }
}
