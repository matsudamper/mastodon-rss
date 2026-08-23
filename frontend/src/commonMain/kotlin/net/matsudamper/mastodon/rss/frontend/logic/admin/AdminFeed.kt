package net.matsudamper.mastodon.rss.frontend.logic.admin

data class AdminFeed(
    val id: String,
    val url: String,
    val title: String?,
    val siteUrl: String?,
    val format: String?,
)

data class AdminFeedPreview(
    val title: String?,
    val siteUrl: String?,
    val format: String,
    val description: String?,
    val itemCount: Int,
    val sampleItems: List<AdminFeedPreviewItem>,
)

data class AdminFeedPreviewItem(
    val title: String?,
    val link: String?,
    val publishedAt: Long?,
)

sealed interface AdminFeedPreviewResult {
    data class Success(
        val preview: AdminFeedPreview,
    ) : AdminFeedPreviewResult

    data class Failure(
        val reason: PreviewFailure,
        val message: String,
    ) : AdminFeedPreviewResult

    enum class PreviewFailure {
        INVALID_URL,
        FETCH_FAILED,
        PARSE_FAILED,
        UNKNOWN,
    }
}

sealed interface AdminSaveFeedResult {
    data class Success(
        val feed: AdminFeed,
    ) : AdminSaveFeedResult

    data class Failure(
        val reason: SaveFailure,
        val message: String,
    ) : AdminSaveFeedResult

    enum class SaveFailure {
        UNKNOWN_ACCOUNT,
        DUPLICATE_URL,
        ALREADY_HAS_FEED,
        INVALID_URL,
        FETCH_FAILED,
        PARSE_FAILED,
        UNKNOWN,
    }
}
