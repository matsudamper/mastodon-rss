package net.matsudamper.mastodon.rss.frontend.preview

import androidx.compose.ui.tooling.preview.Preview

@Preview(
    name = "Phone (wide = false)",
    widthDp = 412,
    heightDp = 915,
)
@Preview(
    name = "Tablet (wide = true)",
    widthDp = 1024,
    heightDp = 1366,
)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class DesktopPreview
