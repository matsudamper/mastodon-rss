package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.ui.text.AnnotatedString

internal expect fun htmlToAnnotatedString(html: String): AnnotatedString
