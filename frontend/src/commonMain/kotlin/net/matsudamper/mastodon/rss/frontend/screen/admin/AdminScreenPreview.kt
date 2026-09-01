package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import net.matsudamper.mastodon.rss.frontend.preview.AndroidScreenPreviews

@AndroidScreenPreviews
@Composable
private fun AdminScreenPreview() {
    MaterialTheme {
        AdminScreen(
            uiState = AdminScreenUiState(AdminScreenUiState.Content.LoggedIn, PreviewAdminListener),
            onClickAccounts = {},
            onClickNewAccount = {},
            onClickRepository = {},
            onClickAdmin = {},
            onClickHome = {},
            passwordField = { _, _ -> },
        )
    }
}

private object PreviewAdminListener : AdminScreenUiState.Listener {
    override fun onPasswordChanged(text: String) = Unit
    override fun onClickLogin() = Unit
    override fun onClickLogout() = Unit
    override fun onClickRetry() = Unit
}
