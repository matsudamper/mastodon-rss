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
                            acct = "@alice@mastodon.example",
                            listener = AndroidPreviewFollowerListener,
                        ),
                        AccountFollowersScreenUiState.Follower(
                            acct = "未取得",
                            listener = AndroidPreviewFollowerListener,
                        ),
                    ),
                    loadMore = AccountFollowersScreenUiState.LoadMore.Button,
                    loadMoreErrorMessage = null,
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
    override fun onClickLoadMore() = Unit
}

private object AndroidPreviewFollowerListener : AccountFollowersScreenUiState.Follower.Listener {
    override fun onClick() = Unit
}
