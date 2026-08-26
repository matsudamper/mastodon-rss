package net.matsudamper.mastodon.rss.frontend.logic.admin

data class AdminFeed(
    val id: Long,
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

    /**
     * サーバーがプレビューを断った
     */
    data class Rejected(
        val reason: PreviewFailure,
    ) : AdminFeedPreviewResult

    /**
     * 応答自体が返ってこなかった
     */
    data class Failure(
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

    /**
     * サーバーが保存を断った
     */
    data class Rejected(
        val reason: SaveFailure,
    ) : AdminSaveFeedResult

    /**
     * 応答自体が返ってこなかった
     */
    data class Failure(
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

data class AdminUnpublishedFeedItem(
    val title: String?,
    val link: String?,
    val publishedAt: Long?,
)

sealed interface AdminUnpublishedFeedItemsResult {
    data class Success(
        val items: List<AdminUnpublishedFeedItem>,
    ) : AdminUnpublishedFeedItemsResult

    data class Rejected(
        val reason: FailureReason,
    ) : AdminUnpublishedFeedItemsResult

    data class Failure(
        val message: String,
    ) : AdminUnpublishedFeedItemsResult

    enum class FailureReason {
        UNKNOWN_ACCOUNT,
        NO_FEED,
        UNKNOWN,
    }
}

sealed interface AdminPostFeedItemsResult {
    data class Success(
        val items: List<AdminUnpublishedFeedItem>,
    ) : AdminPostFeedItemsResult

    data class Rejected(
        val reason: FailureReason,
    ) : AdminPostFeedItemsResult

    data class Failure(
        val message: String,
    ) : AdminPostFeedItemsResult

    enum class FailureReason {
        UNKNOWN_ACCOUNT,
        NO_FEED,
        UNKNOWN,
    }
}
