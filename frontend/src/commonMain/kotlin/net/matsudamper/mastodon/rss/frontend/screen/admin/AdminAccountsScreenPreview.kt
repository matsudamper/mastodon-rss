package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun AdminAccountsScreenPreview() {
    MaterialTheme {
        AdminAccountsScreen(
            uiState = AdminAccountsScreenUiState(
                AdminAccountsScreenUiState.Content.Loaded(
                    listOf(
                        AdminAccountsScreenUiState.Account("rss_news", "@rss_news@example.com", "https://example.com/users/rss_news", "2026-09-01", 42),
                        AdminAccountsScreenUiState.Account("release_notes", "@release_notes@example.com", "https://example.com/users/release_notes", "2026-08-31", 18),
                    ),
                ),
                PreviewAccountsListener,
            ),
            onClickNewAccount = {},
            onClickPublicAccount = {},
            onClickAdminAccount = {},
            onClickAdmin = {},
            onClickHome = {},
        )
    }
}

private object PreviewAccountsListener : AdminAccountsScreenUiState.Listener {
    override fun onClickReload() = Unit
}
