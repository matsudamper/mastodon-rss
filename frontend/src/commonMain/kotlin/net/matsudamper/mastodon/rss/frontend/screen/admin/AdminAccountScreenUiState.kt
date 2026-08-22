package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.runtime.Immutable

data class AdminAccountScreenUiState(
    val acct: String,
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        data object RequireLogin : Content

        data object NotFound : Content

        data class Loaded(
            val account: Account,
            val feed: Feed,
            val post: Post,
            val notes: List<Note>,
            val notesError: String?,
            val notesLoading: Boolean,
            val canLoadMore: Boolean,
            val loadingMore: Boolean,
        ) : Content

        data class Error(
            val message: String,
        ) : Content
    }

    data class Account(
        val accountId: String?,
        val username: String,
        val acct: String,
        val actorUrl: String,
        val createdAt: String?,
        val followerCount: Int,
    )

    data class Feed(
        val registeredUrl: String?,
        val registeredTitle: String?,
        val registeredFormat: String?,
        val inputUrl: String,
        val fetching: Boolean,
        val preview: FeedPreview?,
        val previewError: String?,
        val saving: Boolean,
        val saveError: String?,
    ) {
        val canFetch: Boolean get() = !fetching && !saving && registeredUrl == null && inputUrl.isNotBlank()

        val canSave: Boolean get() = !fetching && !saving && registeredUrl == null && preview != null
    }

    data class FeedPreview(
        val title: String?,
        val siteUrl: String?,
        val format: String,
        val description: String?,
        val itemCount: Int,
        val sampleItems: List<FeedPreviewItem>,
    )

    data class FeedPreviewItem(
        val title: String?,
        val link: String?,
        val publishedAt: String?,
    )

    data class Post(
        val body: String,
        val submitting: Boolean,
        val result: PostResult?,
        val error: String?,
    ) {
        val canSubmit: Boolean get() = !submitting && body.isNotBlank()
    }

    data class Note(
        val url: String,
        val contentHtml: String,
        val publishedAt: String,
    )

    data class PostResult(
        val url: String,
        val targets: Int,
        val delivered: Int,
    )

    @Immutable
    interface Listener {
        fun onFeedUrlChanged(text: String)

        fun onClickFetchFeed()

        fun onClickSaveFeed()

        fun onBodyChanged(text: String)

        fun onClickPost()

        fun onClickLoadMore()

        fun onClickReloadNotes()

        fun onClickReload()
    }
}
