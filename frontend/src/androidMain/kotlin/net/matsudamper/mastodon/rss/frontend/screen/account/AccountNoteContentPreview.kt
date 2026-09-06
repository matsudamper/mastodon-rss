package net.matsudamper.mastodon.rss.frontend.screen.account

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import net.matsudamper.mastodon.rss.frontend.screen.PreviewsMultiSize

@PreviewsMultiSize
@Composable
private fun AccountNoteContentPreview() {
    MaterialTheme {
        AccountNoteContent(
            uiState = AccountNoteScreenUiState(
                content = AccountNoteScreenUiState.Content.Loaded(
                    contentHtml = "Compose Multiplatform の新しい記事を公開しました。",
                    publishedAt = "2026-09-02 12:00",
                ),
                listener = AndroidPreviewAccountNoteListener,
            ),
        )
    }
}

@PreviewsMultiSize
@Composable
private fun AccountNoteContentNotFoundPreview() {
    MaterialTheme {
        AccountNoteContent(
            uiState = AccountNoteScreenUiState(
                content = AccountNoteScreenUiState.Content.NotFound,
                listener = AndroidPreviewAccountNoteListener,
            ),
        )
    }
}

private object AndroidPreviewAccountNoteListener : AccountNoteScreenUiState.Listener {
    override fun onClickClose() = Unit

    override fun onClickReload() = Unit

    override fun onClickActivityPub() = Unit
}
