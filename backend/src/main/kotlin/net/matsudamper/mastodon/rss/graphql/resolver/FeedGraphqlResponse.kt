package net.matsudamper.mastodon.rss.graphql.resolver // pragma: allowlist secret // pragma: allowlist secret

import net.matsudamper.mastodon.rss.graphql.model.QlAdminFeed // pragma: allowlist secret
import net.matsudamper.mastodon.rss.graphql.model.QlAdminFeedPreview // pragma: allowlist secret
import net.matsudamper.mastodon.rss.graphql.model.QlAdminFeedPreviewFailure // pragma: allowlist secret
import net.matsudamper.mastodon.rss.graphql.model.QlAdminFeedPreviewItem // pragma: allowlist secret
import net.matsudamper.mastodon.rss.graphql.model.QlAdminFeedPreviewResult // pragma: allowlist secret
import net.matsudamper.mastodon.rss.graphql.model.QlAdminSaveFeedFailure // pragma: allowlist secret
import net.matsudamper.mastodon.rss.graphql.model.QlAdminSaveFeedResult // pragma: allowlist secret
import net.matsudamper.mastodon.rss.logic.FeedService // pragma: allowlist secret
import net.matsudamper.mastodon.rss.repository.Feed // pragma: allowlist secret

internal fun Feed.toGraphqlResponse(): QlAdminFeed = QlAdminFeed(
    id = id.value.toString(),
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
    when (this) {
        FeedService.PreviewFailure.INVALID_URL -> QlAdminFeedPreviewFailure.INVALID_URL
        FeedService.PreviewFailure.FETCH_FAILED -> QlAdminFeedPreviewFailure.FETCH_FAILED
        FeedService.PreviewFailure.PARSE_FAILED -> QlAdminFeedPreviewFailure.PARSE_FAILED
    }

internal fun FeedService.SaveFailure.toGraphqlResponse(): QlAdminSaveFeedFailure =
    when (this) {
        FeedService.SaveFailure.UNKNOWN_ACCOUNT -> QlAdminSaveFeedFailure.UNKNOWN_ACCOUNT
        FeedService.SaveFailure.DUPLICATE_URL -> QlAdminSaveFeedFailure.DUPLICATE_URL
        FeedService.SaveFailure.ALREADY_HAS_FEED -> QlAdminSaveFeedFailure.ALREADY_HAS_FEED
        FeedService.SaveFailure.INVALID_URL -> QlAdminSaveFeedFailure.INVALID_URL
        FeedService.SaveFailure.FETCH_FAILED -> QlAdminSaveFeedFailure.FETCH_FAILED
        FeedService.SaveFailure.PARSE_FAILED -> QlAdminSaveFeedFailure.PARSE_FAILED
    }

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
            failure = null,
        )

        is FeedService.SaveResult.Failure -> QlAdminSaveFeedResult(
            feed = null,
            failure = reason.toGraphqlResponse(),
        )
    }
