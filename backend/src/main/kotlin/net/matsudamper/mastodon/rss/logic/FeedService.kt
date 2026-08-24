package net.matsudamper.mastodon.rss.logic

import java.time.Instant
import kotlinx.coroutines.CancellationException
import net.matsudamper.mastodon.rss.actor.ActorDirectory
import net.matsudamper.mastodon.rss.feed.FeedFetchService
import net.matsudamper.mastodon.rss.feed.FeedItemKey
import net.matsudamper.mastodon.rss.feed.FeedText
import net.matsudamper.mastodon.rss.feed.HtmlSanitizer
import net.matsudamper.mastodon.rss.feed.ParsedFeedItem
import net.matsudamper.mastodon.rss.feed.toDisplayName
import net.matsudamper.mastodon.rss.note.NotePublisher
import net.matsudamper.mastodon.rss.repository.AccountId
import net.matsudamper.mastodon.rss.repository.AccountRepository
import net.matsudamper.mastodon.rss.repository.Feed
import net.matsudamper.mastodon.rss.repository.FeedItemRepository
import net.matsudamper.mastodon.rss.repository.FeedItemState
import net.matsudamper.mastodon.rss.repository.FeedRepository
import net.matsudamper.mastodon.rss.repository.NewFeed
import net.matsudamper.mastodon.rss.repository.NewFeedItem

class FeedService(
    private val accounts: AccountRepository,
    private val feeds: FeedRepository,
    private val feedItems: FeedItemRepository,
    private val fetcher: FeedFetchService,
    private val actorDirectory: ActorDirectory,
    private val notePublisher: NotePublisher,
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
        postExistingItems: Boolean = false,
    ): SaveResult {
        val account = accounts.findById(accountId)
            ?: return SaveResult.Failure(SaveFailure.UNKNOWN_ACCOUNT)

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
                val imported = importExistingItems(
                    feed = feed,
                    items = fetched.parsed.items,
                    postExistingItems = postExistingItems,
                    username = account.username,
                )
                feeds.markInitialImportDone(feed.id)
                val saved = feeds.find(feed.id) ?: feed.copy(initialImportDone = true)
                SaveResult.Success(
                    feed = saved,
                    postedCount = imported.postedCount,
                    skippedCount = imported.skippedCount,
                )
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
            val postedCount: Int = 0,
            val skippedCount: Int = 0,
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

    private data class ImportedCounts(
        val postedCount: Int,
        val skippedCount: Int,
    )

    private suspend fun importExistingItems(
        feed: Feed,
        items: List<ParsedFeedItem>,
        postExistingItems: Boolean,
        username: String,
    ): ImportedCounts {
        val now = Instant.now()
        var skippedCount = 0
        items.forEach { item ->
            val contentHtml = composeItemHtml(item)
            val state = when {
                contentHtml == null -> FeedItemState.SKIPPED
                !postExistingItems -> FeedItemState.SKIPPED
                else -> FeedItemState.PENDING
            }
            if (state == FeedItemState.SKIPPED) {
                skippedCount += 1
            }
            feedItems.add(
                NewFeedItem(
                    feedId = feed.id,
                    itemKey = FeedItemKey.of(feed.url, item).value,
                    title = item.title,
                    link = item.link,
                    contentHtml = contentHtml,
                    publishedAt = item.publishedAt ?: item.updatedAt,
                    importedAt = now,
                    state = state,
                ),
            )
        }
        if (!postExistingItems) {
            return ImportedCounts(postedCount = 0, skippedCount = skippedCount)
        }
        val sender = actorDirectory.resolve(username)
            ?: return ImportedCounts(postedCount = 0, skippedCount = skippedCount)
        var postedCount = 0
        feedItems.findPending(feed.id, Int.MAX_VALUE).forEach { stored ->
            val html = stored.contentHtml ?: return@forEach
            try {
                notePublisher.publish(sender = sender, contentHtml = html)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return@forEach
            }
            feedItems.markPosted(stored.id, Instant.now())
            postedCount += 1
        }
        return ImportedCounts(postedCount = postedCount, skippedCount = skippedCount)
    }

    private fun composeItemHtml(item: ParsedFeedItem): String? {
        val link = item.link?.trim().orEmpty()
        val title = FeedText.singleLine(item.title.orEmpty()).ifBlank { link }
        if (title.isBlank()) {
            return null
        }
        val text = HtmlSanitizer.escapeText(FeedText.truncate(title, POST_TITLE_MAX_CHARS))
        val html = if (link.isNotBlank()) {
            """<p><a href="${HtmlSanitizer.escapeText(link)}">$text</a></p>"""
        } else {
            "<p>$text</p>"
        }
        val sanitized = HtmlSanitizer.sanitize(html)
        return sanitized.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val DEFAULT_POLL_INTERVAL_SECONDS = 900L
        const val PREVIEW_ITEM_LIMIT = 5
        const val DESCRIPTION_LIMIT = 200
        const val POST_TITLE_MAX_CHARS = 200
    }
}
