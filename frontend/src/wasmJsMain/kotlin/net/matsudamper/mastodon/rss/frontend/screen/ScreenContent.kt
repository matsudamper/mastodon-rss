package net.matsudamper.mastodon.rss.frontend.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.matsudamper.mastodon.rss.frontend.screen.admin.AdminScreenUiState
import net.matsudamper.mastodon.rss.frontend.ui.AdminLoginPasswordField as WasmAdminLoginPasswordField
import net.matsudamper.mastodon.rss.frontend.ui.NoteContent as WasmNoteContent

@Composable
internal actual fun NoteContent(contentHtml: String, modifier: Modifier) {
    WasmNoteContent(contentHtml, modifier)
}

@Composable
internal actual fun AdminLoginPasswordField(
    content: AdminScreenUiState.Content.Login,
    listener: AdminScreenUiState.Listener,
) {
    WasmAdminLoginPasswordField(
        password = content.password,
        onPasswordChange = listener::onPasswordChanged,
        onSubmit = listener::onClickLogin,
        enabled = content.inputEnabled && !content.submitting,
        hasError = content.error != null,
    )
}
