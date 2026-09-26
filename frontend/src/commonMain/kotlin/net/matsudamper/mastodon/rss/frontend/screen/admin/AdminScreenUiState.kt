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

                data class Disabled(
                    val message: String,
                ) : Input
            }
        }

        data class LoggedIn(
            val sections: List<MenuSection>,
            val actorUpdateBroadcast: ActorUpdateBroadcast,
            val listener: LoggedIn.Listener,
        ) : Content {
            @Immutable
            interface Listener {
                fun onClickLogout()
            }
        }

        data class Error(
            val message: String,
        ) : Content
    }

    /**
     * 全アカウントのアカウント情報をフォロワーに配り直す操作。
     *
     * @param resultMessage 前回配り直した結果。まだ配り直していなければ null
     * @param confirmDialogVisible 配り直す前の確認を出している
     */
    data class ActorUpdateBroadcast(
        val buttonLabel: String,
        val buttonEnabled: Boolean,
        val resultMessage: String?,
        val resultIsError: Boolean,
        val confirmDialogVisible: Boolean,
        val listener: Listener,
    ) {
        @Immutable
        interface Listener {
            fun onClickBroadcast()

            fun onClickConfirm()

            fun onDismissConfirm()
        }
    }

    /**
     * ホームに並べる入口のまとまり。見出しの下にタイルを並べる
     */
    data class MenuSection(
        val title: String,
        val items: List<MenuItem>,
    )

    data class MenuItem(
        val title: String,
        val description: String,
        val listener: Listener,
    ) {
        @Immutable
        interface Listener {
            fun onClick()
        }
    }

    @Immutable
    interface Listener : AdminScaffoldListener {
        fun onPasswordChanged(text: String)

        fun onClickLogin()

        fun onClickRetry()
    }
}
