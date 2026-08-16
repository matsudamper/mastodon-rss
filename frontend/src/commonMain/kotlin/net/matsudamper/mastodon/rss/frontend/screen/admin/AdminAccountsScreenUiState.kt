package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.runtime.Immutable

data class AdminAccountsScreenUiState(
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        /** ログインしていない。管理画面のトップに送る */
        data object RequireLogin : Content

        data class Loaded(
            val accounts: List<Account>,
        ) : Content

        data class Error(
            val message: String,
        ) : Content
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

    @Immutable
    interface Listener {
        fun onClickReload()
    }
}
