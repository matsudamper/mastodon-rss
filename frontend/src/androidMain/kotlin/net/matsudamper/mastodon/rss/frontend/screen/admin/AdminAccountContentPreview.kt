package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import net.matsudamper.mastodon.rss.frontend.screen.AndroidPreviewScreenPlatform
import net.matsudamper.mastodon.rss.frontend.screen.PreviewsMultiSize

@PreviewsMultiSize
@Composable
private fun AdminAccountContentPreview() {
    val username = "kotlin"
    MaterialTheme {
        AdminAccountContent(
            uiState = AdminAccountScreenUiState(
                acct = "@$username@example.com",
                content = AdminAccountScreenUiState.Content.Loaded(
                    account = AdminAccountScreenUiState.Account(
                        username = username,
                        acct = "@$username@example.com",
                        actorUrl = "https://example.com/users/$username",
                        iconUrl = null,
                        createdAt = "2026-09-01 10:00",
                        followerCount = 128,
                        displayName = "",
                        summary = "",
                        listener = AndroidPreviewAccountListener,
                    ),
                    feed = AdminAccountScreenUiState.Feed.Registered(
                        url = "https://example.com/feed.xml",
                        title = "Kotlin Updates",
                        format = "Atom 1.0",
                        lastFetchedText = "最終チェック: 2026-09-06 12:34",
                        unpublishedItems = emptyList(),
                        postingUnpublished = false,
                        unpublishedError = null,
                        listener = AndroidPreviewRegisteredFeedListener,
                    ),
                    deliveryQueue = AdminAccountScreenUiState.DeliveryQueue(
                        waitingCount = 3,
                        failedCount = 1,
                        retrying = listOf(
                            AdminAccountScreenUiState.RetryingDelivery(
                                inbox = "https://mastodon.example/inbox",
                                attempts = 2,
                                nextAttemptAt = "2026-09-06 12:36",
                                lastError = "相手が受け取らなかった: 503 Service Unavailable",
                            ),
                        ),
                        retryingMoreText = null,
                        failed = listOf(
                            AdminAccountScreenUiState.FailedDelivery(
                                inbox = "https://gone.example/inbox",
                                attempts = 31,
                                lastError = "POST に失敗した: Connection refused",
                            ),
                        ),
                        failedMoreText = null,
                        listener = AndroidPreviewDeliveryQueueListener,
                    ),
                    post = AdminAccountScreenUiState.Post(
                        body = "新しい記事を公開しました。",
                        submitting = false,
                        result = null,
                        error = null,
                        listener = AndroidPreviewPostListener,
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
                    deleteAccountDialog = null,
                    notesError = null,
                    notesLoading = false,
                    loadMoreVisible = true,
                    loadingMore = false,
                ),
                listener = AndroidPreviewAdminAccountListener,
            ),
            username = username,
            platform = AndroidPreviewScreenPlatform,
        )
    }
}

@PreviewsMultiSize
@Composable
private fun AdminAccountContentNoFeedPreview() {
    val username = "kotlin"
    MaterialTheme {
        AdminAccountContent(
            uiState = AdminAccountScreenUiState(
                acct = "@$username@example.com",
                content = AdminAccountScreenUiState.Content.Loaded(
                    account = AdminAccountScreenUiState.Account(
                        username = username,
                        acct = "@$username@example.com",
                        actorUrl = "https://example.com/users/$username",
                        iconUrl = null,
                        createdAt = "2026-09-01 10:00",
                        followerCount = 0,
                        displayName = "",
                        summary = "",
                        listener = AndroidPreviewAccountListener,
                    ),
                    feed = AdminAccountScreenUiState.Feed.NotRegistered(listener = AndroidPreviewNotRegisteredFeedListener),
                    deliveryQueue = AdminAccountScreenUiState.DeliveryQueue(
                        waitingCount = 0,
                        failedCount = 0,
                        retrying = emptyList(),
                        retryingMoreText = null,
                        failed = emptyList(),
                        failedMoreText = null,
                        listener = AndroidPreviewDeliveryQueueListener,
                    ),
                    post = AdminAccountScreenUiState.Post(
                        body = "",
                        submitting = false,
                        result = null,
                        error = null,
                        listener = AndroidPreviewPostListener,
                    ),
                    notes = emptyList(),
                    deleteNoteDialog = null,
                    deleteAccountDialog = null,
                    notesError = null,
                    notesLoading = false,
                    loadMoreVisible = false,
                    loadingMore = false,
                ),
                listener = AndroidPreviewAdminAccountListener,
            ),
            username = username,
            platform = AndroidPreviewScreenPlatform,
        )
    }
}

private object AndroidPreviewNoteListener : AdminAccountScreenUiState.NoteListener {
    override fun onClickDelete() = Unit
}

private object AndroidPreviewAccountListener : AdminAccountScreenUiState.AccountListener {
    override fun onClickOpenAccount() = Unit

    override fun onClickEditProfile() = Unit

    override fun onClickDelete() = Unit
}

private object AndroidPreviewRegisteredFeedListener : AdminAccountScreenUiState.Feed.RegisteredListener {
    override fun onClickPostLatest() = Unit
}

private object AndroidPreviewNotRegisteredFeedListener : AdminAccountScreenUiState.Feed.NotRegisteredListener {
    override fun onClickAddFeed() = Unit
}

private object AndroidPreviewDeliveryQueueListener : AdminAccountScreenUiState.DeliveryQueueListener {
    override fun onClickReload() = Unit
}

private object AndroidPreviewPostListener : AdminAccountScreenUiState.PostListener {
    override fun onBodyChanged(text: String) = Unit

    override fun onClickPost() = Unit
}

private object AndroidPreviewAdminAccountListener : AdminAccountScreenUiState.Listener {
    override fun onClickHome() = Unit

    override fun onClickAdmin() = Unit

    override fun onClickBackToAdmin() = Unit

    override fun onClickLoadMore() = Unit

    override fun onClickReloadNotes() = Unit

    override fun onClickReload() = Unit
}
