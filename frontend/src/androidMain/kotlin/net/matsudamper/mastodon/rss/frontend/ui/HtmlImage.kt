package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

@Composable
internal actual fun HtmlImage(
    url: String,
    modifier: Modifier,
) {
    AsyncImage(
        model = url,
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
}
