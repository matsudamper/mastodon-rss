package net.matsudamper.mastodon.rss.feed

/** YouTube のチャンネルページに埋め込まれたバナー画像を読む */
fun YouTubeFeedResolver.channelHeaderFromPageHtml(html: String): String? =
    bannerUrl(
        html = html,
        keys = listOf("\"imageBannerViewModel\"", "\"sources\""),
    ) ?: bannerUrl(
        html = html,
        keys = listOf("\"c4TabbedHeaderRenderer\"", "\"banner\"", "\"thumbnails\""),
    )

private fun bannerUrl(
    html: String,
    keys: List<String>,
): String? {
    var index = 0
    for (key in keys) {
        val found = html.indexOf(key, startIndex = index)
        if (found < 0 || found - index > MAX_KEY_DISTANCE) return null
        index = found + key.length
    }

    val arrayStart = html.indexOf('[', startIndex = index)
    if (arrayStart < 0 || arrayStart - index > MAX_KEY_DISTANCE) return null
    val arrayEnd = html.indexOf(']', startIndex = arrayStart + 1)
    if (arrayEnd < 0 || arrayEnd - arrayStart > MAX_SOURCES_LENGTH) return null

    return URL_IN_JSON
        .findAll(html.substring(arrayStart + 1, arrayEnd))
        .map { match -> unescapeJsonUrl(match.groupValues[1]) }
        .filter { url -> url.startsWith("https://") }
        .lastOrNull()
}

private fun unescapeJsonUrl(value: String): String =
    value
        .replace("\\u0026", "&")
        .replace("\\/", "/")

private val URL_IN_JSON = Regex(""""url"\s*:\s*"((?:[^"\\]|\\.)*)"""")
private const val MAX_KEY_DISTANCE = 64 * 1024
private const val MAX_SOURCES_LENGTH = 64 * 1024
