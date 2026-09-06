package net.matsudamper.mastodon.rss.frontend.screen.account

data class AccountUiState(
    val username: String,
    /**
     * Mastodon の検索窓に貼る形
     */
    val acct: String,
    /**
     * ActivityPub の Actor JSON の URL。この画面と対になるもの
     */
    val actorUrl: String,
    val followerCount: String,
    val noteCount: String,
    val feed: FeedUiState?,
) {
    /**
     * アバターの代わりに出す 1 文字
     */
    val initial: String get() = username.first().uppercase()
}

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
)
