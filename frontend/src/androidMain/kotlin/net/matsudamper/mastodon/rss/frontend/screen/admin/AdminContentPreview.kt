package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import net.matsudamper.mastodon.rss.frontend.screen.PreviewsMultiSize
import net.matsudamper.mastodon.rss.frontend.screen.AndroidPreviewScreenPlatform

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
        )
    }
}

private object AndroidPreviewAdminListener : AdminScreenUiState.Listener {
    override fun onClickHome() = Unit

    override fun onClickAdmin() = Unit

    override fun onPasswordChanged(text: String) = Unit

    override fun onClickLogin() = Unit

    override fun onClickLogout() = Unit

    override fun onClickRetry() = Unit

    override fun onClickAccounts() = Unit

    override fun onClickNewAccount() = Unit
}
