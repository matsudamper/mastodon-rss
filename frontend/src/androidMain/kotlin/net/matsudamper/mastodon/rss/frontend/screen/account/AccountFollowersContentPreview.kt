package net.matsudamper.mastodon.rss.frontend.screen.account

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import net.matsudamper.mastodon.rss.frontend.screen.PreviewsMultiSize

@PreviewsMultiSize
@Composable
private fun AccountFollowersContentPreview() {
    MaterialTheme {
        AccountFollowersContent(
            uiState = AccountFollowersScreenUiState(
                content = AccountFollowersScreenUiState.Content.Loaded(
                    followers = listOf(
                        AccountFollowersScreenUiState.Follower(
                            name = "アリス",
                            acct = "@alice@mastodon.example",
                            iconUrl = null,
                            listener = AndroidPreviewFollowerListener,
                        ),
                        AccountFollowersScreenUiState.Follower(
                            name = "@bob@mastodon.example",
                            acct = null,
                            iconUrl = null,
                            listener = AndroidPreviewFollowerListener,
                        ),
                    ),
                    loadMore = AccountFollowersScreenUiState.LoadMore.Loading,
                    listener = AndroidPreviewLoadedListener,
                ),
                listener = AndroidPreviewAccountFollowersListener,
            ),
        )
    }
}

@PreviewsMultiSize
@Composable
private fun AccountFollowersContentEmptyPreview() {
    MaterialTheme {
        AccountFollowersContent(
            uiState = AccountFollowersScreenUiState(
                content = AccountFollowersScreenUiState.Content.Empty,
                listener = AndroidPreviewAccountFollowersListener,
            ),
        )
    }
}

private object AndroidPreviewAccountFollowersListener : AccountFollowersScreenUiState.Listener {
    override fun onClickClose() = Unit
}

private object AndroidPreviewLoadedListener : AccountFollowersScreenUiState.Content.Loaded.Listener {
    override fun onLoadMore() = Unit
}

private object AndroidPreviewFollowerListener : AccountFollowersScreenUiState.Follower.Listener {
    override fun onClick() = Unit
}
