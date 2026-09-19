package net.matsudamper.mastodon.rss.frontend.screen.account

data class AccountUiState(
    val username: String,
    /**
     * 見出しに出す名前。プロフィールが未設定ならユーザー名
     */
    val displayName: String,
    /**
     * Mastodon の検索窓に貼る形
     */
    val acct: String,
    /**
     * ActivityPub の Actor JSON の URL。この画面と対になるもの
     */
    val actorUrl: String,
    /**
     * プロフィール画像の URL。無ければ [initial] を出す
     */
    val iconUrl: String?,
    /**
     * プロフィールヘッダー画像の URL。無ければ背景色を出す
     */
    val headerUrl: String?,
    /**
     * プロフィールの説明文。出すものが無ければ null
     */
    val summary: String?,
    val followerCount: String,
    val noteCount: String,
    val feed: FeedUiState?,
    /**
     * アイコンが無いときにアバターの代わりに出す 1 文字
     */
    val initial: String,
)

data class FeedUiState(
    val feedUrl: String,
    val siteUrl: String?,
)

/**
 * 配信した投稿 1 件
 */
data class NoteUiState(
    val url: String,
    val contentHtml: String,
    val publishedAt: String,
    /**
     * 届いたお気に入りとスタンプ。1 つも届いていなければ null
     */
    val reactions: NoteReactionsUiState?,
    val listener: Listener,
) {
    interface Listener {
        fun onClick()
    }
}

/**
 * 投稿に届いた反応
 *
 * @param favouriteCount お気に入りの数。誰も押していなければ null
 * @param stamps 絵文字のスタンプ。押された数が多い順
 */
data class NoteReactionsUiState(
    val favouriteCount: String?,
    val stamps: List<NoteReactionUiState>,
)

/**
 * 投稿に届いたスタンプ 1 種類
 *
 * @param name 絵文字そのもの、またはカスタム絵文字の名前
 * @param imageUrl カスタム絵文字の画像。読めないときと絵文字そのものは [name] を出す
 */
data class NoteReactionUiState(
    val name: String,
    val imageUrl: String?,
    val count: String,
)
