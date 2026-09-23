package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.HtmlElementView
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLImageElement

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun HtmlImage(
    url: String,
    highlighted: Boolean,
    modifier: Modifier,
) {
    val covered = isHtmlImageCovered()
    HtmlElementView(
        modifier = modifier,
        factory = {
            val image = document.createElement("img") as HTMLImageElement
            image.alt = ""
            image.setAttribute("loading", "lazy")
            image.setAttribute("referrerpolicy", "no-referrer")
            image.style.apply {
                display = "block"
                width = "100%"
                height = "100%"
                setProperty("object-fit", "cover")
                setProperty("pointer-events", "none")
                setProperty("transition", "filter 150ms")
            }
            image
        },
        update = { image ->
            // HtmlElementView は img を div で包み、その div が入力を受け止めて canvas に渡さない。
            // div に入るのは factory から返した後なので、ここで素通しにする
            val wrapper = image.parentElement as? HTMLElement
            wrapper?.style?.setProperty("pointer-events", "none")
            if (image.getAttribute("src") != url) {
                image.src = url
            }
            image.style.setProperty("filter", if (highlighted) "brightness(0.85)" else "none")
            image.style.visibility = if (covered) "hidden" else "visible"
        },
    )
}
