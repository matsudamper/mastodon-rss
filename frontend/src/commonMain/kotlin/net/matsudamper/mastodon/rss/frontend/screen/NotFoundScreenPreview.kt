package net.matsudamper.mastodon.rss.frontend.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import net.matsudamper.mastodon.rss.frontend.preview.AndroidScreenPreviews

@AndroidScreenPreviews
@Composable
private fun NotFoundScreenPreview() {
    MaterialTheme {
        NotFoundScreen(
            requestedPath = "/unknown",
            onClickHome = {},
            onClickAdmin = {},
        )
    }
}
