package net.matsudamper.mastodon.rss.frontend.ui

internal actual fun htmlToPlainText(html: String): String {
    return html.replace(Regex("<[^>]*>"), "").trim()
}
