package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun AdminAccountsContentPreview() {
    MaterialTheme {
        AdminAccountsContent(
            uiState = AdminAccountsScreenUiState(
                content = AdminAccountsScreenUiState.Content.Loaded(
                    accounts = listOf(
                        AdminAccountsScreenUiState.Account(
                            username = "kotlin",
                            acct = "@kotlin@example.com",
                            actorUrl = "https://example.com/users/kotlin",
                            createdAt = "2026-09-01 10:00",
                            followerCount = 128,
                        ),
                        AdminAccountsScreenUiState.Account(
                            username = "android",
                            acct = "@android@example.com",
                            actorUrl = "https://example.com/users/android",
                            createdAt = "2026-09-02 11:00",
                            followerCount = 64,
                        ),
                    ),
                ),
                listener = AndroidPreviewAdminAccountsListener,
            ),
            onClickNewAccount = {},
            onClickPublicAccount = {},
            onClickAdminAccount = {},
            onClickAdmin = {},
            onClickHome = {},
        )
    }
}

private object AndroidPreviewAdminAccountsListener : AdminAccountsScreenUiState.Listener {
    override fun onClickReload() = Unit
}
