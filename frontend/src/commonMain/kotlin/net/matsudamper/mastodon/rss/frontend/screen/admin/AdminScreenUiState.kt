package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.runtime.Immutable
import net.matsudamper.mastodon.rss.frontend.ui.AdminScaffoldListener

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
            val input: Input,
            val passwordInputEnabled: Boolean,
            val loginButtonEnabled: Boolean,
        ) : Content {
            sealed interface Input {
                data object Enabled : Input

                // 無効化してメッセージを出す
                data class Disabled(
                    val message: String,
                ) : Input
            }
        }

        data object LoggedIn : Content

        data class Error(
            val message: String,
        ) : Content
    }

    @Immutable
    interface Listener : AdminScaffoldListener {
        fun onPasswordChanged(text: String)

        fun onClickLogin()

        fun onClickLogout()

        fun onClickRetry()

        fun onClickAccounts()

        fun onClickNewAccount()
    }
}
