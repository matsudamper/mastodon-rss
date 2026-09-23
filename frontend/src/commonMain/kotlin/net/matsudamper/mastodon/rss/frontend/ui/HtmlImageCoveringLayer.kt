package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember

/**
 * HtmlImage がどのダイアログやメニューの中にあるか。外側のものほど小さい
 */
@Immutable
internal class HtmlImageLayer private constructor(private val depth: Int) {
    /**
     * Composable の中で呼ぶと、上に重なるダイアログやメニューが開閉したときに再コンポーズされる
     */
    fun isCovered(): Boolean = openedDepths.any { it > depth }

    fun above(): HtmlImageLayer = HtmlImageLayer(depth + 1)

    fun open() {
        openedDepths.add(depth)
    }

    fun close() {
        openedDepths.remove(depth)
    }

    companion object {
        private val openedDepths = mutableStateListOf<Int>()

        val Base = HtmlImageLayer(0)
    }
}

/**
 * Web の HtmlImage は canvas の上に重なる HTML なので、Compose が描くダイアログやメニューより手前に出てしまう。
 * それらをこの中で出している間は、[parent] 以下の HtmlImage を隠す。
 * 中に HtmlImage を置くときは、content に渡される層を渡す。
 */
@Composable
internal fun HtmlImageCoveringLayer(
    parent: HtmlImageLayer,
    content: @Composable (HtmlImageLayer) -> Unit,
) {
    val layer = remember(parent) { parent.above() }
    DisposableEffect(layer) {
        layer.open()
        onDispose { layer.close() }
    }
    content(layer)
}
