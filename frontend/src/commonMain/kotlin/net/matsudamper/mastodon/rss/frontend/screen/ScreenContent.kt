package net.matsudamper.mastodon.rss.frontend.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.matsudamper.mastodon.rss.frontend.screen.admin.AdminScreenUiState

@Composable
internal expect fun NoteContent(
    contentHtml: String,
    modifier: Modifier,
)

@Composable
internal expect fun AdminLoginPasswordField(
    content: AdminScreenUiState.Content.Login,
    listener: AdminScreenUiState.Listener,
)
