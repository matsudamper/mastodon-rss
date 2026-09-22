package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.HtmlElementView
import kotlinx.browser.document
import org.w3c.dom.HTMLImageElement

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun ExternalImage(
    url: String,
    contentDescription: String,
    onError: () -> Unit,
    modifier: Modifier,
) {
    val currentOnError = rememberUpdatedState(onError)

    HtmlElementView(
        modifier = modifier,
        factory = {
            val image = document.createElement("img") as HTMLImageElement
            image.style.width = "100%"
            image.style.height = "100%"
            // 縦横の比率は配信元ごとに違う。枠に合わせて引き伸ばさない
            image.style.setProperty("object-fit", "contain")
            // 相手のサーバーに、どのページから来たかまでは渡さない
            image.setAttribute("referrerpolicy", "no-referrer")
            image.addEventListener("error") { currentOnError.value() }
            image
        },
        update = { image ->
            image.alt = contentDescription
            if (image.src != url) {
                image.src = url
            }
        },
    )
}
