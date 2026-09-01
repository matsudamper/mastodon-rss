package net.matsudamper.mastodon.rss.frontend.screen.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import net.matsudamper.mastodon.rss.frontend.screen.MultiSizePreview

@MultiSizePreview
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
        )
    }
}

private object AndroidPreviewHomeListener : HomeScreenUiState.Listener {
    override fun onClickHome() = Unit

    override fun onClickAdmin() = Unit

    override fun onClickAccount(username: String) = Unit

    override fun onClickReload() = Unit

    override fun onClickLoadMore() = Unit
}
