package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import coil3.compose.AsyncImage

@Composable
internal actual fun ExternalImage(
    url: String,
    contentDescription: String,
    onError: () -> Unit,
    modifier: Modifier,
) {
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        modifier = modifier,
        onError = { onError() },
    )
}
