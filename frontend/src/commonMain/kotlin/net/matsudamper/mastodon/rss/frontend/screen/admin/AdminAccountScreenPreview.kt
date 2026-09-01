package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import net.matsudamper.mastodon.rss.frontend.preview.DesktopPreview

@DesktopPreview
@Composable
private fun AdminAccountScreenPreview() {
    MaterialTheme {
        AdminAccountScreen(
            username = "rss_news",
            uiState = previewUiState(),
            onClickOpenAccount = {},
            onClickLogin = {},
            onClickAdmin = {},
            onClickHome = {},
            noteContent = { _, modifier ->
                Text(
                    text = "配信済みの記事の本文。",
                    modifier = modifier,
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
        )
    }
}
