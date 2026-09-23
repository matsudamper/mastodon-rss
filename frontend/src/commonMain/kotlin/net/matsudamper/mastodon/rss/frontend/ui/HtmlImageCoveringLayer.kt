package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf

private val LocalHtmlImageLayer = compositionLocalOf { 0 }
private val openedCoveringLayers = mutableStateListOf<Int>()

/**
 * Web の HtmlImage は canvas の上に重なる HTML なので、Compose が描くダイアログやメニューより手前に出てしまう。
 * それらをこの中で出している間は、外側の HtmlImage を隠す。中の HtmlImage は隠さない。
 */
@Composable
internal fun HtmlImageCoveringLayer(content: @Composable () -> Unit) {
    val layer = LocalHtmlImageLayer.current + 1
    DisposableEffect(layer) {
        openedCoveringLayers.add(layer)
        onDispose { openedCoveringLayers.remove(layer) }
    }
    CompositionLocalProvider(LocalHtmlImageLayer provides layer, content = content)
}

@Composable
internal fun isHtmlImageCovered(): Boolean {
    val layer = LocalHtmlImageLayer.current
    return openedCoveringLayers.any { it > layer }
}
