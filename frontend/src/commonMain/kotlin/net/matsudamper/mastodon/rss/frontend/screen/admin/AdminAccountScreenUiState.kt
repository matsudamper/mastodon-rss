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
         * @param deleteNoteDialog 投稿を消す前の確認。出していなければ null
         * @param deleteNoteResult 直前に消した投稿の配信結果。次の削除を始めたら消す
         * @param notesError 一覧を取れなかった理由。投稿の失敗と混ぜない
         * @param notesLoading 一覧を取っている最中
         * @param canLoadMore さらに古い投稿があるか
         */
        data class Loaded(
            val account: Account,
            val feed: Feed,
            val post: Post,
            val notes: List<Note>,
            val deleteNoteDialog: DeleteNoteDialog?,
            val deleteNoteResult: DeleteNoteResult?,
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
     * @param createdAt 「追加: <値>」の形で出す
     */
    data class Account(
        val username: String,
        val acct: String,
        val actorUrl: String,
        val createdAt: String,
        val followerCount: Int,
    )

    sealed interface Feed {
        data class Registered(
            val url: String,
            val title: String?,
            val format: String?,
            val unpublishedItems: List<UnpublishedItem>,
            val postedItems: List<UnpublishedItem>?,
            val postingUnpublished: Boolean,
            val unpublishedError: String?,
        ) : Feed

        /**
         * @param fetching 取得中。ボタンの文字が変わる
         * @param canFetch false の間は取得のボタンを押せなくする
         * @param canSave false の間は登録のボタンを押せなくする
         */
        data class Input(
            val url: String,
            val fetching: Boolean,
            val canFetch: Boolean,
            val saving: Boolean,
            val canSave: Boolean,
            val preview: FeedPreview?,
            val previewError: String?,
            val saveError: String?,
        ) : Feed
    }

    /**
     * 投稿の元になった取り込み済みの記事。
     *
     * @param stateText 投稿の状況。「投稿済み」のような表示用の文字
     * @param deleting 削除中。ボタンを押せなくする
     */
    data class FeedItem(
        val id: Long,
        val title: String?,
        val link: String?,
        val publishedAt: String?,
        val stateText: String,
        val deleting: Boolean,
    )

    data class UnpublishedItem(
        val title: String?,
        val link: String?,
        val publishedAt: String?,
    )

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

    /**
     * 消した投稿の `Delete` の配信結果。
     *
     * @param targets 送った宛先の数
     * @param delivered そのうち届いた数。少なければ、その相手には投稿が残っている
     */
    data class DeleteNoteResult(
        val targets: Int,
        val delivered: Int,
    )

    /**
     * 投稿を消す前の確認。
     *
     * @param hasFeedItem 元になった記事があるか。あるときだけ、まとめて消すかを選べる
     * @param deleting 削除中。ボタンを押せなくする
     */
    data class DeleteNoteDialog(
        val hasFeedItem: Boolean,
        val deleting: Boolean,
    )

    /**
     * @param feedItem 元になった記事。手で書いた投稿と、記事を消した後は null
     */
    data class Note(
        val id: String,
        val url: String,
        val contentHtml: String,
        val publishedAt: String,
        val feedItem: FeedItem?,
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

        fun onClickPostLatest()

        fun onBodyChanged(text: String)

        fun onClickPost()

        fun onClickLoadMore()

        /**
         * 投稿の元になった記事を消す。配信した投稿は残るので、
         * 最新情報を投稿すると同じ記事がもう一度流れる
         */
        fun onClickDeleteFeedItem(id: Long)

        /**
         * 投稿を消す確認を出す
         */
        fun onClickDeleteNote(id: String)

        fun onDismissDeleteNote()

        /**
         * @param withFeedItem 元になった記事も消す。消すと最新情報を投稿したときに
         *   取り込み直されてもう一度流れる
         */
        fun onConfirmDeleteNote(withFeedItem: Boolean)

        /**
         * 一覧だけ取り直す
         */
        fun onClickReloadNotes()

        fun onClickReload()
    }
}
