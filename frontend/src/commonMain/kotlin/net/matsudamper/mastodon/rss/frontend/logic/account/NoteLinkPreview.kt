package net.matsudamper.mastodon.rss.frontend.logic.account

/**
 * 投稿の本文にあるリンク 1 つ分の OGP。取れなかった項目は null
 */
data class NoteLinkPreview(
    val url: String,
    val title: String?,
    val siteName: String?,
    val imageUrl: String?,
)

sealed interface NoteLinkPreviewsResult {
    data class Success(
        val previews: List<NoteLinkPreview>,
    ) : NoteLinkPreviewsResult

    data class Failure(
        val message: String,
    ) : NoteLinkPreviewsResult
}
