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
                    account = AccountNoteScreenUiState.Account(
                        username = "tech_blog",
                        displayName = "Tech Blog",
                        acct = "@tech_blog@example.com",
                        iconUrl = null,
                    ),
                    contentHtml = "Compose Multiplatform の新しい記事を公開しました。",
                    publishedAt = "2026-09-02 12:00",
                    favouriteCount = "3",
                    stamps = listOf(
                        NoteStampUiState(name = "👍", imageUrl = null, count = "2"),
                    ),
                    linkPreviews = listOf(
                        AccountNoteScreenUiState.LinkPreview(
                            url = "https://example.com/compose-multiplatform",
                            title = "Compose Multiplatform の新しい記事",
                            siteName = "example.com",
                            imageUrl = null,
                        ),
                    ),
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

    override fun onClickLinkPreview(url: String) = Unit
}
