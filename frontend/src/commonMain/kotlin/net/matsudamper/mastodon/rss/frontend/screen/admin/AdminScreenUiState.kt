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
            val input: Input,
        ) : Content {
            val inputEnabled: Boolean get() = input is Input.Enabled

            sealed interface Input {
                data object Enabled : Input

                // 無効化してメッセージを出す
                data class Disabled(
                    val message: String,
                ) : Input
            }
        }

        data class LoggedIn(
            val accounts: Accounts,
            val addAccount: AddAccount,
        ) : Content

        data class Error(
            val message: String,
        ) : Content
    }

    sealed interface Accounts {
        data object Loading : Accounts

        data class Loaded(
            val items: List<Account>,
        ) : Accounts

        data class Error(
            val message: String,
        ) : Accounts
    }

    /**
     * @param fromConfigLabel 設定で決まるアカウントに付ける印。追加したものには付かない
     */
    data class Account(
        val username: String,
        val acct: String,
        val actorUrl: String,
        val fromConfigLabel: String?,
        val createdAt: String?,
    )

    data class AddAccount(
        val username: String,
        val submitting: Boolean,
        val error: String?,
    ) {
        val canSubmit: Boolean get() = !submitting && username.isNotBlank()
    }

    @Immutable
    interface Listener {
        fun onPasswordChanged(text: String)

        fun onClickLogin()

        fun onClickLogout()

        fun onClickRetry()

        fun onAddAccountUsernameChanged(text: String)

        fun onClickAddAccount()

        fun onClickReloadAccounts()
    }
}
