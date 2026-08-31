package net.matsudamper.mastodon.rss.frontend

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import net.matsudamper.mastodon.rss.frontend.screen.admin.AdminAccountScreen
import net.matsudamper.mastodon.rss.frontend.screen.admin.previewUiState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "mastodon-rss Preview",
    ) {
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
}
