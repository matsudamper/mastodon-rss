package net.matsudamper.mastodon.rss.frontend.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.matsudamper.mastodon.rss.frontend.screen.admin.AdminScreenUiState

internal interface ScreenPlatform {
    val host: String

    fun openExternalLink(url: String)

    fun copyToClipboard(text: String, onResult: (Boolean) -> Unit)

    @Composable
    fun NoteContent(
        contentHtml: String,
        modifier: Modifier,
    )

    @Composable
    fun AdminLoginPasswordField(
        content: AdminScreenUiState.Content.Login,
        listener: AdminScreenUiState.Listener,
    )
}
