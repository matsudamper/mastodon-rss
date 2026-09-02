package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
internal fun NoteContent(
    contentHtml: String,
    modifier: Modifier = Modifier,
) {
    val text = remember(contentHtml) { htmlToPlainText(contentHtml) }
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

internal expect fun htmlToPlainText(html: String): String
