package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.ui.text.AnnotatedString
import kotlinx.browser.document
import org.w3c.dom.Element
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.Node
import org.w3c.dom.Text

internal actual fun htmlToAnnotatedString(html: String): AnnotatedString {
    if (html.isBlank()) return AnnotatedString("")

    val div = document.createElement("div") as HTMLDivElement
    div.innerHTML = html

    val builder = StringBuilder()
    appendTextFromNode(div, builder)
    return AnnotatedString(builder.toString().trim())
}

private fun appendTextFromNode(node: Node, builder: StringBuilder) {
    when (node) {
        is Text -> builder.append(node.textContent.orEmpty())

        is Element -> {
            val tag = node.nodeName.lowercase()
            when (tag) {
                "br" -> builder.append('\n')

                else -> {
                    val isBlock = tag in BLOCK_TAGS
                    if (isBlock) {
                        appendBlockBoundary(builder)
                    }
                    for (index in 0 until node.childNodes.length) {
                        val child = node.childNodes.item(index) ?: continue
                        appendTextFromNode(child, builder)
                    }
                    if (isBlock) {
                        appendBlockBoundary(builder)
                    }
                }
            }
        }
    }
}

private fun appendBlockBoundary(builder: StringBuilder) {
    if (builder.isEmpty() || builder.endsWith('\n')) return

    builder.append('\n')
}

private val BLOCK_TAGS =
    setOf(
        "p",
        "div",
        "li",
        "ul",
        "ol",
        "h1",
        "h2",
        "h3",
        "h4",
        "h5",
        "h6",
        "blockquote",
        "pre",
        "tr",
    )
