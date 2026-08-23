package net.matsudamper.mastodon.rss.logic

import java.time.Instant
import net.matsudamper.mastodon.rss.feed.FeedFetchService
import net.matsudamper.mastodon.rss.feed.FeedFormat
import net.matsudamper.mastodon.rss.feed.FeedText
import net.matsudamper.mastodon.rss.feed.ParsedFeed
import net.matsudamper.mastodon.rss.feed.ParsedFeedItem
import net.matsudamper.mastodon.rss.feed.toDisplayName
import net.matsudamper.mastodon.rss.repository.AccountId
import net.matsudamper.mastodon.rss.repository.AccountRepository
import net.matsudamper.mastodon.rss.repository.Feed
import net.matsudamper.mastodon.rss.repository.FeedRepository
import net.matsudamper.mastodon.rss.repository.NewFeed

class FeedService(
    private val accounts: AccountRepository,
    private val feeds: FeedRepository,
    private val fetcher: FeedFetchService,
) {
    suspend fun preview(url: String): PreviewResult {
        return when (val fetched = fetcher.fetch(url)) {
            is FeedFetchService.FetchResult.Success -> PreviewResult.Success(fetched.toPreview())
            FeedFetchService.FetchResult.InvalidUrl -> PreviewResult.Failure(PreviewFailure.INVALID_URL)
            FeedFetchService.FetchResult.TooLarge -> PreviewResult.Failure(PreviewFailure.FETCH_FAILED)
            is FeedFetchService.FetchResult.HttpError -> PreviewResult.Failure(PreviewFailure.FETCH_FAILED)
            is FeedFetchService.FetchResult.ParseError -> PreviewResult.Failure(PreviewFailure.PARSE_FAILED)
        }
    }

    suspend fun save(
        accountId: AccountId,
        url: String,
    ): SaveResult {
        if (accounts.findById(accountId) == null) {
            return SaveResult.Failure(SaveFailure.UNKNOWN_ACCOUNT)
        }

        if (feeds.findByAccountId(accountId) != null) {
            return SaveResult.Failure(SaveFailure.ALREADY_HAS_FEED)
        }

        return when (val fetched = fetcher.fetch(url)) {
            is FeedFetchService.FetchResult.Success -> {
                if (feeds.findByUrl(fetched.feedUrl) != null) {
                    return SaveResult.Failure(SaveFailure.DUPLICATE_URL)
                }

                val feed = feeds.add(
                    NewFeed(
                        accountId = accountId,
                        url = fetched.feedUrl,
                        title = fetched.parsed.title,
                        siteUrl = fetched.parsed.link,
                        format = fetched.parsed.format.toDisplayName(),
                        pollIntervalSeconds = DEFAULT_POLL_INTERVAL_SECONDS,
                    ),
                ) ?: return SaveResult.Failure(
                    if (feeds.findByAccountId(accountId) != null) {
                        SaveFailure.ALREADY_HAS_FEED
                    } else {
                        SaveFailure.DUPLICATE_URL
                    },
                )
                SaveResult.Success(feed)
            }

            FeedFetchService.FetchResult.InvalidUrl -> SaveResult.Failure(SaveFailure.INVALID_URL)

            FeedFetchService.FetchResult.TooLarge -> SaveResult.Failure(SaveFailure.FETCH_FAILED)

            is FeedFetchService.FetchResult.HttpError -> SaveResult.Failure(SaveFailure.FETCH_FAILED)

            is FeedFetchService.FetchResult.ParseError -> SaveResult.Failure(SaveFailure.PARSE_FAILED)
        }
    }

    fun findByAccountId(accountId: AccountId): Feed? = feeds.findByAccountId(accountId)

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
        val publishedAt: Instant?,
    )

    sealed interface PreviewResult {
        data class Success(
            val preview: FeedPreview,
        ) : PreviewResult

        data class Failure(
            val reason: PreviewFailure,
        ) : PreviewResult
    }

    enum class PreviewFailure {
        INVALID_URL,
        FETCH_FAILED,
        PARSE_FAILED,
    }

    sealed interface SaveResult {
        data class Success(
            val feed: Feed,
        ) : SaveResult

        data class Failure(
            val reason: SaveFailure,
        ) : SaveResult
    }

    enum class SaveFailure {
        UNKNOWN_ACCOUNT,
        DUPLICATE_URL,
        ALREADY_HAS_FEED,
        INVALID_URL,
        FETCH_FAILED,
        PARSE_FAILED,
    }

    private fun FeedFetchService.FetchResult.Success.toPreview(): FeedPreview {
        return FeedPreview(
            title = parsed.title,
            siteUrl = parsed.link,
            format = parsed.format.toDisplayName(),
            description = parsed.description?.toPlainText()?.let { truncateDescription(it) },
            itemCount = parsed.items.size,
            sampleItems = parsed.items.take(PREVIEW_ITEM_LIMIT).map { it.toPreviewItem() },
        )
    }

    private fun ParsedFeedItem.toPreviewItem(): FeedPreviewItem = FeedPreviewItem(
        title = title,
        link = link,
        publishedAt = publishedAt ?: updatedAt,
    )

    private fun truncateDescription(text: String): String {
        val normalized = FeedText.singleLine(text)
        return FeedText.truncate(normalized, DESCRIPTION_LIMIT)
    }

    private companion object {
        const val DEFAULT_POLL_INTERVAL_SECONDS = 900L
        const val PREVIEW_ITEM_LIMIT = 5
        const val DESCRIPTION_LIMIT = 200
    }
}
