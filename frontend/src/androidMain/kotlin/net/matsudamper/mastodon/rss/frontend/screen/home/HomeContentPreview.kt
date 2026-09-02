package net.matsudamper.mastodon.rss.frontend.screen.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import net.matsudamper.mastodon.rss.frontend.screen.PreviewsMultiSize

@PreviewsMultiSize
@Composable
private fun HomeContentPreview() {
    MaterialTheme {
        HomeContent(
            uiState = HomeScreenUiState(
                content = HomeScreenUiState.Content.Loaded(
                    accounts = listOf(
                        HomeScreenUiState.Account("kotlin", "@kotlin@example.com"),
                        HomeScreenUiState.Account("android", "@android@example.com"),
                    ),
                    hasMore = true,
                    isLoadingMore = false,
                    loadMoreErrorMessage = null,
                ),
                listener = AndroidPreviewHomeListener,
            ),
            onClickAccount = {},
            onClickHome = {},
            onClickAdmin = {},
        )
    }
}

private object AndroidPreviewHomeListener : HomeScreenUiState.Listener {
    override fun onClickReload() = Unit

    override fun onClickLoadMore() = Unit
}
