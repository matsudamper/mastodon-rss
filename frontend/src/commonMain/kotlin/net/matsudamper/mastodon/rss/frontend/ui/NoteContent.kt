package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

@Composable
internal fun NoteContent(
    contentHtml: String,
    modifier: Modifier = Modifier,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val text = remember(contentHtml, linkColor) {
        linkifyUrls(
            text = htmlToAnnotatedString(contentHtml).text,
            linkColor = linkColor,
        )
    }
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

private fun linkifyUrls(
    text: String,
    linkColor: Color,
) = buildAnnotatedString {
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = linkColor,
            textDecoration = TextDecoration.Underline,
        ),
    )
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

private val URL_REGEX = Regex("https?://\\S+")

private val TRAILING_URL_PUNCTUATION = charArrayOf(
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
