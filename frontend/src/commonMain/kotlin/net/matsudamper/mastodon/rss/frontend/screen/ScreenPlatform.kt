package net.matsudamper.mastodon.rss.frontend.screen

internal interface ScreenPlatform {
    val host: String

    fun openExternalLink(url: String)

    fun copyToClipboard(text: String, onResult: (Boolean) -> Unit)

}
