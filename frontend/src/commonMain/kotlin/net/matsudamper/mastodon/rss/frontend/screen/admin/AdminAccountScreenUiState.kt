package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.runtime.Immutable

data class AdminAccountScreenUiState(
    val acct: String,
    val content: Content,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        /**
         * ログインしていない。管理画面のトップに送る
         */
        data object RequireLogin : Content

        /**
         * その名前のアカウントが無い
         */
        data object NotFound : Content

        /**
         * @param account この画面が扱うアカウント
         * @param feed RSS フィードの登録状況と入力欄
         * @param post 投稿の入力欄
         * @param notes 配信した投稿。新しい順
         * @param notesError 一覧を取れなかった理由。投稿の失敗と混ぜない
         * @param notesLoading 一覧を取っている最中
         * @param canLoadMore さらに古い投稿があるか
         */
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

    /**
     * @param acct Mastodon の検索窓に貼る形
     * @param createdAt 「追加: <値>」の形で出す。null ならこの行を出さない
     */
    data class Account(
        val accountId: String?,
        val username: String,
        val acct: String,
        val actorUrl: String,
        val createdAt: String?,
        val followerCount: Int,
    )

    /**
     * @param registrable 登録先に指定できるアカウントか。指定できないなら入力欄を出さない
     */
    data class Feed(
        val registrable: Boolean,
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
        val canFetch: Boolean get() = registrable && !fetching && !saving && registeredUrl == null && inputUrl.isNotBlank()

        val canSave: Boolean get() = registrable && !fetching && !saving && registeredUrl == null && preview != null
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

    /**
     * @param submitting true の間は入力欄とボタンを押せなくする
     * @param result 直前の投稿の結果。次の入力を始めたら消す
     */
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

    /**
     * @param targets 送った宛先の数
     * @param delivered そのうち届いた数
     */
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

        /**
         * 一覧だけ取り直す
         */
        fun onClickReloadNotes()

        fun onClickReload()
    }
}
