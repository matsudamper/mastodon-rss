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
         * @param profile 登録済みフィードがあるときのプロフィール編集
         * @param post 投稿の入力欄
         * @param notes 配信した投稿。新しい順
         * @param notesError 一覧を取れなかった理由。投稿の失敗と混ぜない
         * @param notesLoading 一覧を取っている最中
         * @param canLoadMore さらに古い投稿があるか
         */
        data class Loaded(
            val account: Account,
            val feed: Feed,
            val profile: Profile?,
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
     * @param createdAt 「追加: <値>」の形で出す
     */
    data class Account(
        val username: String,
        val acct: String,
        val actorUrl: String,
        val createdAt: String,
        val followerCount: Int,
        val displayName: String,
        val summary: String,
    )

    sealed interface Feed {
        data class Registered(
            val url: String,
            val title: String?,
            val format: String?,
        ) : Feed

        /**
         * @param fetching 取得中。ボタンの文字が変わる
         * @param canFetch false の間は取得のボタンを押せなくする
         * @param canSave false の間は保存のボタンを押せなくする
         * @param overwriteConfirm 既存プロフィールを上書きするか確認する
         */
        data class Input(
            val url: String,
            val displayName: String,
            val summary: String,
            val fetching: Boolean,
            val canFetch: Boolean,
            val saving: Boolean,
            val canSave: Boolean,
            val preview: FeedPreview?,
            val previewError: String?,
            val saveError: String?,
            val overwriteConfirm: ProfileOverwriteConfirm?,
        ) : Feed {
            val canEditProfile: Boolean get() = !fetching && !saving && overwriteConfirm == null
        }
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
     * @param beforeDisplayName 上書き前の表示名
     * @param beforeSummary 上書き前の説明文
     * @param afterDisplayName 保存しようとしている表示名
     * @param afterSummary 保存しようとしている説明文
     */
    data class ProfileOverwriteConfirm(
        val beforeDisplayName: String,
        val beforeSummary: String,
        val afterDisplayName: String,
        val afterSummary: String,
    )

    /**
     * @param editing true のとき入力欄を出す
     * @param editDisplayName 編集中の表示名
     * @param editSummary 編集中の説明文
     */
    data class Profile(
        val displayName: String,
        val summary: String,
        val editing: Boolean,
        val editDisplayName: String,
        val editSummary: String,
        val saving: Boolean,
        val error: String?,
    ) {
        val canSave: Boolean get() = !saving && editDisplayName.isNotBlank()
    }

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

        fun onFeedProfileDisplayNameChanged(text: String)

        fun onFeedProfileSummaryChanged(text: String)

        fun onClickFetchFeed()

        fun onClickSaveFeed()

        fun onClickConfirmProfileOverwrite()

        fun onClickSkipProfileOverwrite()

        fun onClickEditProfile()

        fun onClickCancelProfileEdit()

        fun onProfileDisplayNameChanged(text: String)

        fun onProfileSummaryChanged(text: String)

        fun onClickSaveProfile()

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
