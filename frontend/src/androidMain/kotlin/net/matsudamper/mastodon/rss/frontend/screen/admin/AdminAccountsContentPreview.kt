package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import net.matsudamper.mastodon.rss.frontend.screen.PreviewsMultiSize

@PreviewsMultiSize
@Composable
private fun AdminAccountsContentPreview() {
    MaterialTheme {
        AdminAccountsContent(
            uiState = AdminAccountsScreenUiState(
                content = AdminAccountsScreenUiState.Content.Loaded(
                    accounts = listOf(
                        AdminAccountsScreenUiState.Account(
                            username = "kotlin",
                            displayName = "Kotlin",
                            acct = "@kotlin@example.com",
                            actorUrl = "https://example.com/users/kotlin",
                            iconUrl = null,
                            createdAt = "2026-09-01 10:00",
                            followerCount = 128,
                        ),
                        AdminAccountsScreenUiState.Account(
                            username = "android",
                            displayName = "Android",
                            acct = "@android@example.com",
                            actorUrl = "https://example.com/users/android",
                            iconUrl = null,
                            createdAt = "2026-09-02 11:00",
                            followerCount = 64,
                        ),
                    ),
                    loadMoreVisible = true,
                    loadingMore = false,
                    loadMoreErrorMessage = null,
                    loadMoreButtonText = "もっと見る",
                ),
                listener = AndroidPreviewAdminAccountsListener,
            ),
        )
    }
}

private object AndroidPreviewAdminAccountsListener : AdminAccountsScreenUiState.Listener {
    override fun onClickHome() = Unit

    override fun onClickAdmin() = Unit

    override fun onClickNewAccount() = Unit

    override fun onClickPublic(username: String) = Unit

    override fun onClickAccount(username: String) = Unit

    override fun onClickReload() = Unit

    override fun onClickLoadMore() = Unit
}
