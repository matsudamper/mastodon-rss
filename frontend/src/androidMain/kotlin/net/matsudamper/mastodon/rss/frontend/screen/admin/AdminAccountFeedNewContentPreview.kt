package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import net.matsudamper.mastodon.rss.frontend.screen.PreviewsMultiSize

@PreviewsMultiSize
@Composable
private fun AdminAccountFeedNewContentPreview() {
    MaterialTheme {
        AdminAccountFeedNewContent(
            uiState = AdminAccountFeedNewScreenUiState(
                acct = "@kotlin",
                url = "",
                urlInputEnabled = true,
                fetching = false,
                fetchButtonEnabled = false,
                saving = false,
                saveButtonEnabled = false,
                closeEnabled = true,
                preview = null,
                errorMessage = null,
                listener = AndroidPreviewAdminAccountFeedNewListener,
            ),
        )
    }
}

@PreviewsMultiSize
@Composable
private fun AdminAccountFeedNewContentFetchedPreview() {
    MaterialTheme {
        AdminAccountFeedNewContent(
            uiState = AdminAccountFeedNewScreenUiState(
                acct = "@kotlin",
                url = "https://example.com/feed.xml",
                urlInputEnabled = true,
                fetching = false,
                fetchButtonEnabled = true,
                saving = false,
                saveButtonEnabled = true,
                closeEnabled = true,
                preview = AdminAccountFeedNewScreenUiState.Preview(
                    title = "Kotlin Updates",
                    siteUrl = "https://example.com",
                    format = "Atom 1.0",
                    description = "Kotlin の更新情報",
                    itemCount = 24,
                    sampleItems = listOf(
                        AdminAccountFeedNewScreenUiState.PreviewItem(
                            title = "Compose Multiplatform 1.12 が出た",
                            link = "https://example.com/articles/1",
                            publishedAt = "2026-09-02 12:00",
                        ),
                        AdminAccountFeedNewScreenUiState.PreviewItem(
                            title = null,
                            link = "https://example.com/articles/2",
                            publishedAt = null,
                        ),
                    ),
                ),
                errorMessage = null,
                listener = AndroidPreviewAdminAccountFeedNewListener,
            ),
        )
    }
}

private object AndroidPreviewAdminAccountFeedNewListener : AdminAccountFeedNewScreenUiState.Listener {
    override fun onUrlChanged(text: String) = Unit

    override fun onClickFetch() = Unit

    override fun onClickSave() = Unit

    override fun onClickClose() = Unit
}
