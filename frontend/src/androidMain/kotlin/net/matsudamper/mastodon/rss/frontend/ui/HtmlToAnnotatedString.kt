package net.matsudamper.mastodon.rss.frontend.ui

import android.text.Html
import androidx.compose.ui.text.AnnotatedString

internal actual fun htmlToAnnotatedString(html: String): AnnotatedString {
    return AnnotatedString(Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim())
}
