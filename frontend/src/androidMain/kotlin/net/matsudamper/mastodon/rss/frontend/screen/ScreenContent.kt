package net.matsudamper.mastodon.rss.frontend.screen

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun NoteContent(contentHtml: String, modifier: Modifier) {
    Text(text = contentHtml, modifier = modifier)
}
