package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import net.matsudamper.mastodon.rss.frontend.screen.ScreenPlatform

@Preview
@Composable
private fun AdminScreenPreview() {
    MaterialTheme {
        AdminContent(
            uiState = PreviewAdminScreenUiState,
            platform = PreviewScreenPlatform,
            onClickAccounts = {},
            onClickNewAccount = {},
            onClickAdmin = {},
            onClickHome = {},
        )
    }
}

private val PreviewAdminScreenUiState = AdminScreenUiState(
    content = AdminScreenUiState.Content.LoggedIn,
    listener = PreviewAdminScreenListener,
)

private object PreviewAdminScreenListener : AdminScreenUiState.Listener {
    override fun onPasswordChanged(text: String) = Unit

    override fun onClickLogin() = Unit

    override fun onClickLogout() = Unit

    override fun onClickRetry() = Unit
}

private object PreviewScreenPlatform : ScreenPlatform {
    override val host: String = "example.com"

    override fun openExternalLink(url: String) = Unit

    override fun copyToClipboard(text: String, onResult: (Boolean) -> Unit) = Unit

    @Composable
    override fun NoteContent(contentHtml: String, modifier: Modifier) = Unit

    @Composable
    override fun AdminLoginPasswordField(
        content: AdminScreenUiState.Content.Login,
        listener: AdminScreenUiState.Listener,
    ) = Unit
}
