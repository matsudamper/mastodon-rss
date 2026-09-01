package net.matsudamper.mastodon.rss.frontend.screen.account

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import net.matsudamper.mastodon.rss.frontend.screen.AndroidPreviewScreenPlatform
import net.matsudamper.mastodon.rss.frontend.screen.MultiSizePreview
import net.matsudamper.mastodon.rss.frontend.screen.rememberPreviewNavigationEvents

@MultiSizePreview
@Composable
private fun AccountContentPreview() {
    val username = "kotlin"
    val navigationEvents = rememberPreviewNavigationEvents()
    MaterialTheme {
        AccountContent(
            uiState = AccountScreenUiState(
                content = AccountScreenUiState.Content.Loaded(
                    account = AccountUiState.placeholder(
                        username = username,
                        acct = "@$username@example.com",
                        actorUrl = "https://example.com/users/$username",
                        host = AndroidPreviewScreenPlatform.host,
                    ),
                    notes = listOf(
                        NoteUiState(
                            url = "https://example.com/notes/1",
                            contentHtml = "Compose Multiplatform の新しい記事を公開しました。",
                            publishedAt = "2026-09-02 12:00",
                        ),
                    ),
                    notesError = null,
                    notesLoading = false,
                    canLoadMore = true,
                    loadingMore = false,
                ),
                listener = AndroidPreviewAccountListener,
            ),
            username = username,
            platform = AndroidPreviewScreenPlatform,
            navigationEvents = navigationEvents,
        )
    }
}

private object AndroidPreviewAccountListener : AccountScreenUiState.Listener {
    override fun onClickReload() = Unit

    override fun onClickReloadNotes() = Unit

    override fun onClickLoadMore() = Unit

    override fun onClickCopyAcct() = Unit
}
