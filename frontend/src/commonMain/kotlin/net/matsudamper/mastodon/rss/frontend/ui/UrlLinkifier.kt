package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink

internal class UrlLinkifier(
    private val linkStyles: TextLinkStyles,
) {
    fun linkify(text: String): AnnotatedString = buildAnnotatedString {
        var currentIndex = 0

        URL_REGEX.findAll(text).forEach { match ->
            val matchStart = match.range.first
            val matchEndExclusive = match.range.last + 1
            val url = match.value.trimEnd(*TRAILING_URL_PUNCTUATION)
            val urlEndExclusive = matchStart + url.length

            append(text.substring(currentIndex, matchStart))
            withLink(LinkAnnotation.Url(url, linkStyles)) {
                append(url)
            }
            append(text.substring(urlEndExclusive, matchEndExclusive))
            currentIndex = matchEndExclusive
        }

        append(text.substring(currentIndex))
    }

    private companion object {
        val URL_REGEX = Regex("https?://\\S+")

        val TRAILING_URL_PUNCTUATION = charArrayOf(
            '.',
            ',',
            ';',
            ':',
            '!',
            '?',
            ')',
            ']',
            '}',
            '。',
            '、',
            '！',
            '？',
        )
    }
}
