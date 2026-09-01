package net.matsudamper.mastodon.rss.frontend.screen

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.matsudamper.mastodon.rss.frontend.screen.admin.AdminScreenUiState

internal object AndroidPreviewScreenPlatform : ScreenPlatform {
    override val host: String = "example.com"

    override fun openExternalLink(url: String) = Unit

    override fun copyToClipboard(text: String, onResult: (Boolean) -> Unit) = Unit

    @Composable
    override fun NoteContent(contentHtml: String, modifier: Modifier) {
        Text(text = contentHtml, modifier = modifier)
    }

    @Composable
    override fun AdminLoginPasswordField(
        content: AdminScreenUiState.Content.Login,
        listener: AdminScreenUiState.Listener,
    ) {
        OutlinedTextField(
            value = content.password,
            onValueChange = listener::onPasswordChanged,
            enabled = content.inputEnabled && !content.submitting,
            label = { Text("パスワード") },
        )
    }
}
