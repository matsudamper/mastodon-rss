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
                        "https://mastodon.example/users/alice",
                        "https://social.example/users/bob",
                    ).map { actorUrl ->
                        FollowerUiState(
                            actorUrl = actorUrl,
                            listener = AndroidPreviewFollowerListener,
                        )
                    },
                    loadMoreButtonVisible = true,
                    loadMoreButtonLoading = false,
                    loadMoreErrorMessage = null,
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

    override fun onClickReload() = Unit

    override fun onClickLoadMore() = Unit
}

private object AndroidPreviewFollowerListener : FollowerUiState.Listener {
    override fun onClick() = Unit
}
