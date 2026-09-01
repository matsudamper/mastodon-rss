package net.matsudamper.mastodon.rss.frontend.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun NotFoundContentPreview() {
    MaterialTheme {
        NotFoundContent(
            requestedPath = "/missing",
            description = "ページが存在しません",
        )
    }
}
