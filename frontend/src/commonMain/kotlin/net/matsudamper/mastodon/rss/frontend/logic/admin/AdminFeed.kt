package net.matsudamper.mastodon.rss.frontend.logic.admin

import net.matsudamper.mastodon.rss.shared.FeedItemId

/**
 * @param lastFetchedAt 最後に取りに行った時刻。一度も取りに行っていなければ null
 */
data class AdminFeed(
    val id: Long,
    val url: String,
    val title: String?,
    val siteUrl: String?,
    val format: String?,
    val lastFetchedAt: Long?,
)

/**
 * @param description 一覧に並べる用に 1 行へ潰した説明
 * @param fullDescription 配信元が書いたままの説明。プロフィールに取り込むときに使う
 */
data class AdminFeedPreview(
    val title: String?,
    val siteUrl: String?,
    val format: String,
    val description: String?,
    val fullDescription: String?,
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
    /**
     * @param importedCount 今回の取得で新しく取り込めた記事の件数
     */
    data class Success(
        val importedCount: Int,
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
        INVALID_URL,
        FETCH_FAILED,
        PARSE_FAILED,
        UNKNOWN,
    }
}

/**
 * 取り込んだ記事 1 件
 */
data class AdminFeedItem(
    val id: FeedItemId,
    val title: String?,
    val link: String?,
    val publishedAt: Long?,
)

sealed interface AdminDeleteFeedItemsResult {
    data object Success : AdminDeleteFeedItemsResult

    data class Rejected(
        val reason: FailureReason,
    ) : AdminDeleteFeedItemsResult

    data class Failure(
        val message: String,
    ) : AdminDeleteFeedItemsResult

    enum class FailureReason {
        UNKNOWN_ACCOUNT,
        NO_FEED,
        NOT_FOUND,
        UNKNOWN,
    }
}
