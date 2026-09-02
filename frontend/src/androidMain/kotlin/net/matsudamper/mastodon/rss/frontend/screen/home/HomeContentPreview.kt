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
                        HomeScreenUiState.Account(
                            username = "kotlin",
                            acct = "@kotlin@example.com",
                            onClick = {},
                        ),
                        HomeScreenUiState.Account(
                            username = "android",
                            acct = "@android@example.com",
                            onClick = {},
                        ),
                    ),
                    hasMore = true,
                    isLoadingMore = false,
                    loadMoreErrorMessage = null,
                ),
                listener = AndroidPreviewHomeListener,
            ),
        )
    }
}

private object AndroidPreviewHomeListener : HomeScreenUiState.Listener {
    override fun onClickHome() = Unit

    override fun onClickAdmin() = Unit

    override fun onClickReload() = Unit

    override fun onClickLoadMore() = Unit
}
