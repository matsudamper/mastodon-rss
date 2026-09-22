package net.matsudamper.mastodon.rss.frontend.screen.accounts

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import net.matsudamper.mastodon.rss.frontend.screen.PreviewsMultiSize

@PreviewsMultiSize
@Composable
private fun AccountsContentPreview() {
    MaterialTheme {
        AccountsContent(
            uiState = AccountsScreenUiState(
                content = AccountsScreenUiState.Content.Loaded(
                    accounts = listOf(
                        AccountsScreenUiState.Account(
                            username = "kotlin",
                            acct = "@kotlin@example.com",
                            displayName = "Kotlin",
                            iconUrl = null,
                        ),
                        AccountsScreenUiState.Account(
                            username = "android",
                            acct = "@android@example.com",
                            displayName = "Android",
                            iconUrl = null,
                        ),
                    ),
                    loadMoreVisible = true,
                    loadingMore = false,
                    loadMoreErrorMessage = null,
                ),
                listener = AndroidPreviewAccountsListener,
            ),
        )
    }
}

private object AndroidPreviewAccountsListener : AccountsScreenUiState.Listener {
    override fun onClickHome() = Unit

    override fun onClickAdmin() = Unit

    override fun onClickReload() = Unit

    override fun onClickLoadMore() = Unit

    override fun onClickAccount(username: String) = Unit
}
