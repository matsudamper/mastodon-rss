package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import net.matsudamper.mastodon.rss.frontend.screen.AndroidPreviewScreenPlatform
import net.matsudamper.mastodon.rss.frontend.screen.MultiSizePreview
import net.matsudamper.mastodon.rss.frontend.screen.PreviewNavigation

@MultiSizePreview
@Composable
private fun AdminAccountContentPreview() {
    val username = "kotlin"
    MaterialTheme {
        PreviewNavigation {
            AdminAccountContent(
                uiState = AdminAccountScreenUiState(
                    acct = "@$username@example.com",
                    content = AdminAccountScreenUiState.Content.Loaded(
                        account = AdminAccountScreenUiState.Account(
                            username = username,
                            acct = "@$username@example.com",
                            actorUrl = "https://example.com/users/$username",
                            createdAt = "2026-09-01 10:00",
                            followerCount = 128,
                        ),
                        feed = AdminAccountScreenUiState.Feed.Registered(
                            url = "https://example.com/feed.xml",
                            title = "Kotlin Updates",
                            format = "Atom 1.0",
                            unpublishedItems = emptyList(),
                            postedItems = null,
                            postingUnpublished = false,
                            unpublishedError = null,
                        ),
                        post = AdminAccountScreenUiState.Post(
                            body = "新しい記事を公開しました。",
                            submitting = false,
                            result = null,
                            error = null,
                        ),
                        notes = listOf(
                            AdminAccountScreenUiState.Note(
                                url = "https://example.com/notes/1",
                                contentHtml = "Compose Multiplatform の新しい記事を公開しました。",
                                publishedAt = "2026-09-02 12:00",
                                sourceArticle = null,
                                listener = AndroidPreviewNoteListener,
                            ),
                        ),
                        deleteNoteDialog = null,
                        notesError = null,
                        notesLoading = false,
                        canLoadMore = true,
                        loadingMore = false,
                    ),
                    listener = AndroidPreviewAdminAccountListener,
                ),
                username = username,
                platform = AndroidPreviewScreenPlatform,
            )
        }
    }
}

private object AndroidPreviewNoteListener : AdminAccountScreenUiState.NoteListener {
    override fun onClickDelete() = Unit
}

private object AndroidPreviewAdminAccountListener : AdminAccountScreenUiState.Listener {
    override fun onFeedUrlChanged(text: String) = Unit

    override fun onClickFetchFeed() = Unit

    override fun onClickSaveFeed() = Unit

    override fun onClickPostLatest() = Unit

    override fun onBodyChanged(text: String) = Unit

    override fun onClickPost() = Unit

    override fun onClickLoadMore() = Unit

    override fun onDismissDeleteNote() = Unit

    override fun onConfirmDeleteNote(deleteSourceArticle: Boolean) = Unit

    override fun onClickReloadNotes() = Unit

    override fun onClickReload() = Unit
}
