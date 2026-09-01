package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import net.matsudamper.mastodon.rss.frontend.screen.MultiSizePreview
import net.matsudamper.mastodon.rss.frontend.screen.rememberPreviewNavigationEvents

@MultiSizePreview
@Composable
private fun AdminAccountNewContentPreview() {
    val navigationEvents = rememberPreviewNavigationEvents()
    MaterialTheme {
        AdminAccountNewContent(
            uiState = AdminAccountNewScreenUiState(
                content = AdminAccountNewScreenUiState.Content.Input(
                    username = "kotlin",
                    submitting = false,
                    error = null,
                ),
                listener = AndroidPreviewAdminAccountNewListener,
            ),
            navigationEvents = navigationEvents,
        )
    }
}

private object AndroidPreviewAdminAccountNewListener : AdminAccountNewScreenUiState.Listener {
    override fun onUsernameChanged(text: String) = Unit

    override fun onClickAdd() = Unit

    override fun onClickAddAnother() = Unit
}
