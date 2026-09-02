package net.matsudamper.mastodon.rss.frontend.screen

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.matsudamper.mastodon.rss.frontend.screen.admin.AdminScreenUiState

@Composable
internal actual fun NoteContent(contentHtml: String, modifier: Modifier) {
    Text(text = contentHtml, modifier = modifier)
}

@Composable
internal actual fun AdminLoginPasswordField(
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
