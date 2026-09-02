package net.matsudamper.mastodon.rss.frontend.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal expect fun NoteContent(
    contentHtml: String,
    modifier: Modifier,
)
