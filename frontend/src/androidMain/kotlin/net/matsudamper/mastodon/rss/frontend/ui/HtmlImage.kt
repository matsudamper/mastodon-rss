package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/**
 * 同じ canvas に描くのでリップルやダイアログは画像の上にも出る。[layer] と [highlighted] は使わない
 */
@Composable
internal actual fun HtmlImage(
    layer: HtmlImageLayer,
    url: String,
    highlighted: Boolean,
    modifier: Modifier,
) {
    AsyncImage(
        model = url,
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
}
