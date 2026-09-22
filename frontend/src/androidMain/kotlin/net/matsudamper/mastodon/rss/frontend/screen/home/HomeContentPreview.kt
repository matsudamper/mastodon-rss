package net.matsudamper.mastodon.rss.frontend.screen.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import net.matsudamper.mastodon.rss.frontend.screen.PreviewsMultiSize

@PreviewsMultiSize
@Composable
private fun HomeContentPreview() {
    val kotlin = HomeScreenUiState.Account(
        username = "kotlin",
        acct = "@kotlin@example.com",
        displayName = "Kotlin",
        iconUrl = null,
        listener = AndroidPreviewAccountListener,
    )
    val android = HomeScreenUiState.Account(
        username = "android",
        acct = "@android@example.com",
        displayName = "Android",
        iconUrl = null,
        listener = AndroidPreviewAccountListener,
    )
    MaterialTheme {
        HomeContent(
            uiState = HomeScreenUiState(
                timeline = HomeScreenUiState.Timeline.Loaded(
                    notes = listOf(
                        HomeScreenUiState.Note(
                            url = "https://example.com/notes/1",
                            contentHtml = "<p>Kotlin 2.4 がリリースされました</p>",
                            publishedAt = "2026-08-09 11:02",
                            account = kotlin,
                            listener = AndroidPreviewNoteListener,
                        ),
                        HomeScreenUiState.Note(
                            url = "https://example.com/notes/2",
                            contentHtml = "<p>Android 17 のベータが出ました</p>",
                            publishedAt = "2026-08-09 10:00",
                            account = android,
                            listener = AndroidPreviewNoteListener,
                        ),
                    ),
                    loadMoreVisible = true,
                    loadingMore = false,
                    loadMoreErrorMessage = null,
                ),
                accounts = HomeScreenUiState.Accounts.Loaded(
                    accounts = listOf(kotlin, android),
                ),
                listener = AndroidPreviewHomeListener,
            ),
            onOpenExternal = {},
        )
    }
}

private object AndroidPreviewHomeListener : HomeScreenUiState.Listener {
    override fun onClickHome() = Unit

    override fun onClickAdmin() = Unit

    override fun onClickReloadTimeline() = Unit

    override fun onClickLoadMore() = Unit

    override fun onClickReloadAccounts() = Unit

    override fun onClickAllAccounts() = Unit
}

private object AndroidPreviewNoteListener : HomeScreenUiState.NoteListener {
    override fun onClick() = Unit
}

private object AndroidPreviewAccountListener : HomeScreenUiState.AccountListener {
    override fun onClick() = Unit
}
