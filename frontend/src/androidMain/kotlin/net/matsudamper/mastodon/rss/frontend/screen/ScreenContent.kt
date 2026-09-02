package net.matsudamper.mastodon.rss.frontend.screen

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun NoteContent(contentHtml: String, modifier: Modifier) {
    Text(text = contentHtml, modifier = modifier)
}

@Composable
internal actual fun AdminLoginPasswordField(
    password: String,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    enabled: Boolean,
    hasError: Boolean,
) {
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        enabled = enabled,
        isError = hasError,
        label = { Text("パスワード") },
    )
}
