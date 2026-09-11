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
            val url = trimTrailingPunctuation(match.value)
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

    private fun trimTrailingPunctuation(value: String): String {
        var endExclusive = value.length
        while (endExclusive > 0) {
            val last = value[endExclusive - 1]
            when {
                last in TRAILING_URL_PUNCTUATION -> endExclusive--

                last in CLOSING_BRACKETS -> {
                    val opening = CLOSING_BRACKETS.getValue(last)
                    val candidate = value.substring(0, endExclusive)
                    val openingCount = candidate.count { it == opening }
                    val closingCount = candidate.count { it == last }
                    if (closingCount > openingCount) {
                        endExclusive--
                    } else {
                        break
                    }
                }

                else -> break
            }
        }
        return value.substring(0, endExclusive)
    }

    private companion object {
        val URL_REGEX = Regex(
            pattern = "https?://[A-Za-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+",
            option = RegexOption.IGNORE_CASE,
        )

        val TRAILING_URL_PUNCTUATION = setOf(
            '.',
            ',',
            ';',
            ':',
            '!',
            '?',
            '。',
            '、',
            '！',
            '？',
            '）',
            '］',
            '｝',
        )

        val CLOSING_BRACKETS = mapOf(
            ')' to '(',
            ']' to '[',
            '}' to '{',
        )
    }
}
