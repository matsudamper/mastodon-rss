package net.matsudamper.mastodon.rss.frontend.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.matsudamper.mastodon.rss.frontend.ui.AdminLoginPasswordField as WasmAdminLoginPasswordField
import net.matsudamper.mastodon.rss.frontend.ui.NoteContent as WasmNoteContent

@Composable
internal actual fun NoteContent(contentHtml: String, modifier: Modifier) {
    WasmNoteContent(contentHtml, modifier)
}

@Composable
internal actual fun AdminLoginPasswordField(
    password: String,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    enabled: Boolean,
    hasError: Boolean,
) {
    WasmAdminLoginPasswordField(
        password = password,
        onPasswordChange = onPasswordChange,
        onSubmit = onSubmit,
        enabled = enabled,
        hasError = hasError,
    )
}
