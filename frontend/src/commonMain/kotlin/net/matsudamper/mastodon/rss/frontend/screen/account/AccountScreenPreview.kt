package net.matsudamper.mastodon.rss.frontend.screen.account

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import net.matsudamper.mastodon.rss.frontend.preview.AndroidScreenPreviews

@AndroidScreenPreviews
@Composable
private fun AccountScreenPreview() {
    MaterialTheme {
        AccountScreen(
            username = "rss_news",
            uiState = AccountScreenUiState(
                AccountScreenUiState.Content.Loaded(
                    account = AccountUiState.placeholder("rss_news", "@rss_news@example.com", "https://example.com/users/rss_news", "example.com"),
                    notes = listOf(NoteUiState("https://example.com/notes/1", "<p>新しい記事を公開しました。</p>", "2026-09-01 10:00")),
                    notesError = null,
                    notesLoading = false,
                    canLoadMore = true,
                    loadingMore = false,
                ),
                PreviewAccountListener,
            ),
            onClickHome = {},
            onClickAdmin = {},
            onClickOperator = {},
            onOpenExternal = {},
            noteContent = { html, modifier -> Text(html.replace("<p>", "").replace("</p>", ""), modifier) },
        )
    }
}

private object PreviewAccountListener : AccountScreenUiState.Listener {
    override fun onClickReload() = Unit
    override fun onClickReloadNotes() = Unit
    override fun onClickLoadMore() = Unit
    override fun onClickCopyAcct() = Unit
}
