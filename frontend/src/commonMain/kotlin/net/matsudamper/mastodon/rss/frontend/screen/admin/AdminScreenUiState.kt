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
            // 無効化してメッセージを出す
            val enabled: Boolean,
            val submitting: Boolean,
            val message: String?,
            val error: String?,
        ) : Content

        data object LoggedIn : Content

        data class Error(
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
