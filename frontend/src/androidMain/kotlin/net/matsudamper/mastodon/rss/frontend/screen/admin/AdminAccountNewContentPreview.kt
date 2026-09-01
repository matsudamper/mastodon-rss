package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun AdminAccountNewContentPreview() {
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
            onClickAccounts = {},
            onClickAdmin = {},
            onClickHome = {},
        )
    }
}

private object AndroidPreviewAdminAccountNewListener : AdminAccountNewScreenUiState.Listener {
    override fun onUsernameChanged(text: String) = Unit

    override fun onClickAdd() = Unit

    override fun onClickAddAnother() = Unit
}
