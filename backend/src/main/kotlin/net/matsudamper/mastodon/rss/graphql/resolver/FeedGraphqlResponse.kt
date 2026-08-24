package net.matsudamper.mastodon.rss.graphql.resolver

import net.matsudamper.mastodon.rss.graphql.model.QlAdminFeedPreview
import net.matsudamper.mastodon.rss.graphql.model.QlAdminFeedPreviewFailure
import net.matsudamper.mastodon.rss.graphql.model.QlAdminFeedPreviewFailureReason
import net.matsudamper.mastodon.rss.graphql.model.QlAdminFeedPreviewItem
import net.matsudamper.mastodon.rss.graphql.model.QlAdminFeedPreviewResult
import net.matsudamper.mastodon.rss.graphql.model.QlAdminSaveFeedFailure
import net.matsudamper.mastodon.rss.graphql.model.QlAdminSaveFeedFailureReason
import net.matsudamper.mastodon.rss.graphql.model.QlAdminSaveFeedResult
import net.matsudamper.mastodon.rss.graphql.model.QlFeed
import net.matsudamper.mastodon.rss.logic.FeedService
import net.matsudamper.mastodon.rss.repository.Feed
import net.matsudamper.mastodon.rss.shared.FeedId

internal fun Feed.toGraphqlResponse(): QlFeed = QlFeed(
    id = FeedId(id.value),
    url = url,
    title = title,
    siteUrl = siteUrl,
    format = format,
    createdAt = createdAt.epochSecond,
)

internal fun FeedService.FeedPreview.toGraphqlResponse(): QlAdminFeedPreview = QlAdminFeedPreview(
    title = title,
    siteUrl = siteUrl,
    format = format,
    description = description,
    itemCount = itemCount,
    sampleItems = sampleItems.map { item ->
        QlAdminFeedPreviewItem(
            title = item.title,
            link = item.link,
            publishedAt = item.publishedAt?.epochSecond,
        )
    },
)

internal fun FeedService.PreviewFailure.toGraphqlResponse(): QlAdminFeedPreviewFailure =
    QlAdminFeedPreviewFailure(
        reason = when (this) {
            FeedService.PreviewFailure.INVALID_URL -> QlAdminFeedPreviewFailureReason.INVALID_URL
            FeedService.PreviewFailure.FETCH_FAILED -> QlAdminFeedPreviewFailureReason.FETCH_FAILED
            FeedService.PreviewFailure.PARSE_FAILED -> QlAdminFeedPreviewFailureReason.PARSE_FAILED
        },
    )

internal fun FeedService.SaveFailure.toGraphqlResponse(): QlAdminSaveFeedFailure =
    QlAdminSaveFeedFailure(
        reason = when (this) {
            FeedService.SaveFailure.UNKNOWN_ACCOUNT -> QlAdminSaveFeedFailureReason.UNKNOWN_ACCOUNT
            FeedService.SaveFailure.DUPLICATE_URL -> QlAdminSaveFeedFailureReason.DUPLICATE_URL
            FeedService.SaveFailure.ALREADY_HAS_FEED -> QlAdminSaveFeedFailureReason.ALREADY_HAS_FEED
            FeedService.SaveFailure.INVALID_URL -> QlAdminSaveFeedFailureReason.INVALID_URL
            FeedService.SaveFailure.FETCH_FAILED -> QlAdminSaveFeedFailureReason.FETCH_FAILED
            FeedService.SaveFailure.PARSE_FAILED -> QlAdminSaveFeedFailureReason.PARSE_FAILED
        },
    )

internal fun FeedService.PreviewResult.toGraphqlResponse(): QlAdminFeedPreviewResult =
    when (this) {
        is FeedService.PreviewResult.Success -> QlAdminFeedPreviewResult(
            preview = preview.toGraphqlResponse(),
            failure = null,
        )

        is FeedService.PreviewResult.Failure -> QlAdminFeedPreviewResult(
            preview = null,
            failure = reason.toGraphqlResponse(),
        )
    }

internal fun FeedService.SaveResult.toGraphqlResponse(): QlAdminSaveFeedResult =
    when (this) {
        is FeedService.SaveResult.Success -> QlAdminSaveFeedResult(
            feed = feed.toGraphqlResponse(),
            postedCount = postedCount,
            skippedCount = skippedCount,
            failure = null,
        )

        is FeedService.SaveResult.Failure -> QlAdminSaveFeedResult(
            feed = null,
            postedCount = null,
            skippedCount = null,
            failure = reason.toGraphqlResponse(),
        )
    }
