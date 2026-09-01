package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import net.matsudamper.mastodon.rss.frontend.screen.AndroidPreviewScreenPlatform
import net.matsudamper.mastodon.rss.frontend.screen.PreviewsMultiSize

@PreviewsMultiSize
@Composable
private fun AdminContentPreview() {
    MaterialTheme {
        AdminContent(
            uiState = AdminScreenUiState(
                content = AdminScreenUiState.Content.LoggedIn,
                listener = AndroidPreviewAdminListener,
            ),
            platform = AndroidPreviewScreenPlatform,
            onClickAccounts = {},
            onClickNewAccount = {},
            onClickAdmin = {},
            onClickHome = {},
        )
    }
}

private object AndroidPreviewAdminListener : AdminScreenUiState.Listener {
    override fun onPasswordChanged(text: String) = Unit

    override fun onClickLogin() = Unit

    override fun onClickLogout() = Unit

    override fun onClickRetry() = Unit
}
