package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun AdminAccountScreenPreview() {
    MaterialTheme {
        AdminAccountScreenContent(
            uiState = previewUiState(),
            wide = true,
            onClickOpenAccount = {},
            onClickLogin = {},
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
