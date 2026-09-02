package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.ui.text.AnnotatedString
import kotlinx.browser.document
import org.w3c.dom.HTMLDivElement

internal actual fun htmlToAnnotatedString(html: String): AnnotatedString {
    if (html.isBlank()) return AnnotatedString("")

    val div = document.createElement("div") as HTMLDivElement
    div.innerHTML = html
    return AnnotatedString(div.innerText.trim())
}
