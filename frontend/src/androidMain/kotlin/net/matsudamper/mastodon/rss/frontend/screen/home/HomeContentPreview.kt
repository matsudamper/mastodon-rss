package net.matsudamper.mastodon.rss.frontend.screen.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import net.matsudamper.mastodon.rss.frontend.screen.MultiSizePreview
import net.matsudamper.mastodon.rss.frontend.screen.rememberPreviewNavigationEvents

@MultiSizePreview
@Composable
private fun HomeContentPreview() {
    val navigationEvents = rememberPreviewNavigationEvents()
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
            navigationEvents = navigationEvents,
        )
    }
}

private object AndroidPreviewHomeListener : HomeScreenUiState.Listener {
    override fun onClickReload() = Unit

    override fun onClickLoadMore() = Unit
}
