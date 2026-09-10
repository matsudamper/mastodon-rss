package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.runtime.Immutable
import net.matsudamper.mastodon.rss.frontend.ui.AdminScaffoldListener

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
         * @param deliveryQueue フォロワーへの配信の待ち状況
         * @param postDialog 投稿ダイアログ。出していなければ null
         * @param notes 配信した投稿。新しい順
         * @param deleteNoteDialog 投稿を消す前の確認。出していなければ null
         * @param deleteAccountDialog アカウントを消す前の確認。出していなければ null
         * @param notesError 一覧を取れなかった理由。投稿の失敗と混ぜない
         * @param notesLoading 一覧を取っている最中
         * @param loadMoreVisible 「もっと見る」を出すか
         */
        data class Loaded(
            val account: Account,
            val feed: Feed,
            val deliveryQueue: DeliveryQueue,
            val postDialog: Post?,
            val notes: List<Note>,
            val deleteNoteDialog: DeleteNoteDialog?,
            val deleteAccountDialog: DeleteAccountDialog?,
            val notesError: String?,
            val notesLoading: Boolean,
            val loadMoreVisible: Boolean,
            val loadingMore: Boolean,
        ) : Content

        data class Error(
            val message: String,
        ) : Content
    }

    /**
     * @param acct Mastodon の検索窓に貼る形
     * @param iconUrl アイコン画像。無ければ null
     * @param createdAt 「追加: <値>」の形で出す
     */
    data class Account(
        val username: String,
        val acct: String,
        val actorUrl: String,
        val iconUrl: String?,
        val createdAt: String,
        val followerCount: Int,
        val displayName: String,
        val summary: String,
        val listener: AccountListener,
    )

    @Immutable
    interface AccountListener {
        fun onClickOpenAccount()

        fun onClickEditProfile()

        fun onClickNewPost()

        /**
         * このアカウントを消す確認を出す
         */
        fun onClickDelete()
    }

    sealed interface Feed {
        /**
         * @param lastFetchedText 最後に取りに行ったのがいつかを出す一行
         */
        data class Registered(
            val url: String,
            val title: String?,
            val format: String?,
            val lastFetchedText: String,
            val unpublishedItems: List<UnpublishedItem>,
            val postingUnpublished: Boolean,
            val unpublishedError: String?,
            val listener: RegisteredListener,
            val postLatestButtonEnabled: Boolean,
        ) : Feed

        /**
         * 追加はダイアログの画面に分けてあるので、ここに置くのは入口だけ
         */
        data class NotRegistered(
            val listener: NotRegisteredListener,
        ) : Feed

        @Immutable
        interface RegisteredListener {
            /**
             * フィードの最新を取り込み、未投稿を投稿する
             */
            fun onClickPostLatest()
        }

        @Immutable
        interface NotRegisteredListener {
            fun onClickAddFeed()
        }
    }

    /**
     * フォロワーへの配信の待ち状況。
     *
     * @param waitingCount まだ届いていない配信
     * @param failedCount 届けるのを諦めた配信
     * @param retrying 送り直しを待っている配信。一部だけ
     * @param retryingMoreText [retrying] に載せ切れなかった分があることを伝える一行。無ければ null
     * @param failed 諦めた配信。一部だけ
     * @param failedMoreText [failed] に載せ切れなかった分があることを伝える一行。無ければ null
     */
    data class DeliveryQueue(
        val waitingCount: Int,
        val failedCount: Int,
        val retrying: List<RetryingDelivery>,
        val retryingMoreText: String?,
        val failed: List<FailedDelivery>,
        val failedMoreText: String?,
        val listener: DeliveryQueueListener,
    ) {
        val retryingSectionVisible: Boolean get() = retrying.isNotEmpty()
        val failedSectionVisible: Boolean get() = failed.isNotEmpty()
    }

    @Immutable
    interface DeliveryQueueListener {
        /**
         * 最新の配信状況にする。開いたままにしていると、出している値は開いた時点のまま古くなる
         */
        fun onClickReload()
    }

    /**
     * @param nextAttemptAt 次に送る時刻
     */
    data class RetryingDelivery(
        val inbox: String,
        val attempts: Int,
        val nextAttemptAt: String,
        val lastError: String?,
    )

    data class FailedDelivery(
        val inbox: String,
        val attempts: Int,
        val lastError: String?,
    )

    /**
     * 投稿と一緒に見せる、元になった記事
     *
     * @param deleting 削除中。ボタンを押せなくする
     */
    data class SourceArticle(
        val title: String?,
        val link: String?,
        val publishedAt: String?,
        val deleting: Boolean,
        val listener: SourceArticleListener,
        val deleteButtonEnabled: Boolean,
    )

    @Immutable
    interface SourceArticleListener {
        /**
         * この記事を消す。配信した投稿は残るので、
         * 最新情報を投稿すると同じ記事がもう一度流れる
         */
        fun onClickDelete()
    }

    data class UnpublishedItem(
        val title: String?,
        val link: String?,
        val publishedAt: String?,
    )

    /**
     * @param submitting true の間は入力欄とボタンを押せなくする
     */
    data class Post(
        val body: String,
        val submitting: Boolean,
        val error: String?,
        val listener: PostListener,
        val bodyInputEnabled: Boolean,
        val postButtonEnabled: Boolean,
        val closeEnabled: Boolean,
    )

    @Immutable
    interface PostListener {
        fun onBodyChanged(text: String)

        fun onClickPost()

        fun onDismiss()
    }

    /**
     * 投稿を消す前の確認。
     *
     * @param hasSourceArticle 元になった記事があるか。あるときだけ、まとめて消すかを選べる
     * @param deleting 削除中。ボタンを押せなくする
     */
    data class DeleteNoteDialog(
        val hasSourceArticle: Boolean,
        val deleting: Boolean,
        val listener: DeleteNoteDialogListener,
        val confirmButtonEnabled: Boolean,
        val deleteNoteOnlyButtonEnabled: Boolean,
        val closeEnabled: Boolean,
    )

    @Immutable
    interface DeleteNoteDialogListener {
        /**
         * @param deleteSourceArticle 元になった記事も消す。消すと最新情報を投稿したときに
         *   取り込み直されてもう一度流れる
         */
        fun onClickConfirm(deleteSourceArticle: Boolean)

        fun onDismiss()
    }

    data class DeleteAccountDialog(
        val message: String,
        val confirmLabel: String,
        val confirmButtonEnabled: Boolean,
        val closeEnabled: Boolean,
        val errorMessage: String?,
        val listener: DeleteAccountDialogListener,
    )

    @Immutable
    interface DeleteAccountDialogListener {
        fun onClickConfirm()

        fun onDismiss()
    }

    /**
     * @param sourceArticle 元になった記事。無い投稿では出さない
     */
    data class Note(
        val url: String,
        val contentHtml: String,
        val publishedAt: String,
        val sourceArticle: SourceArticle?,
        val listener: NoteListener,
    )

    @Immutable
    interface NoteListener {
        /**
         * この投稿を消す確認を出す
         */
        fun onClickDelete()
    }

    /**
     * 画面全体に関わる操作。1 つの部品に閉じるものはその UiState が持つ
     */
    @Immutable
    interface Listener : AdminScaffoldListener {
        fun onClickBackToAdmin()

        fun onClickLoadMore()

        /**
         * 一覧だけ取り直す
         */
        fun onClickReloadNotes()

        fun onClickReload()
    }
}
