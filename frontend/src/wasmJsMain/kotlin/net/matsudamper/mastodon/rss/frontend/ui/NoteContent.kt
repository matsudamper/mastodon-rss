package net.matsudamper.mastodon.rss.frontend.ui

import kotlinx.browser.document
import org.w3c.dom.HTMLDivElement

internal actual fun htmlToPlainText(html: String): String {
    if (html.isBlank()) return ""

    val div = document.createElement("div") as HTMLDivElement
    div.innerHTML = html
    return div.innerText.trim()
}
