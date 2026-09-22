package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.HtmlElementView
import kotlinx.browser.document
import org.w3c.dom.HTMLImageElement

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun HtmlImage(
    url: String,
    modifier: Modifier,
) {
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
                // 押したときは下にある Compose の部品に届かせる
                setProperty("pointer-events", "none")
            }
            image
        },
        update = { image ->
            if (image.getAttribute("src") != url) {
                image.src = url
            }
        },
    )
}
