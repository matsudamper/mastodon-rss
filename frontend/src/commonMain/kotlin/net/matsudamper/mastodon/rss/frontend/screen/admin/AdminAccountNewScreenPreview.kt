package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import net.matsudamper.mastodon.rss.frontend.preview.DesktopPreview

@DesktopPreview
@Composable
private fun AdminAccountNewScreenPreview() {
    MaterialTheme {
        AdminAccountNewScreen(
            AdminAccountNewScreenUiState(
                AdminAccountNewScreenUiState.Content.Input("rss_news", submitting = false, error = null),
                PreviewNewAccountListener,
            ),
            onClickAccounts = {},
            onClickAdmin = {},
            onClickHome = {},
        )
    }
}

private object PreviewNewAccountListener : AdminAccountNewScreenUiState.Listener {
    override fun onUsernameChanged(text: String) = Unit
    override fun onClickAdd() = Unit
    override fun onClickAddAnother() = Unit
}
