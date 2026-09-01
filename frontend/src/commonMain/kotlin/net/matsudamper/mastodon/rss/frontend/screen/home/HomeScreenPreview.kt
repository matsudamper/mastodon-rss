package net.matsudamper.mastodon.rss.frontend.screen.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import net.matsudamper.mastodon.rss.frontend.preview.AndroidScreenPreviews

@AndroidScreenPreviews
@Composable
private fun HomeScreenPreview() {
    MaterialTheme {
        HomeScreen(
            uiState = HomeScreenUiState(
                content = HomeScreenUiState.Content.Loaded(
                    accounts = listOf(
                        HomeScreenUiState.Account("rss_news", "@rss_news@example.com"),
                        HomeScreenUiState.Account("release_notes", "@release_notes@example.com"),
                    ),
                    hasMore = true,
                    isLoadingMore = false,
                    loadMoreErrorMessage = null,
                ),
                listener = PreviewListener,
            ),
            onClickAccount = {},
            onClickHome = {},
            onClickAdmin = {},
        )
    }
}

private object PreviewListener : HomeScreenUiState.Listener {
    override fun onClickReload() = Unit
    override fun onClickLoadMore() = Unit
}
