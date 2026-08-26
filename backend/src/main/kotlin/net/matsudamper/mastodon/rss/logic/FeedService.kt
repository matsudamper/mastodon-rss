package net.matsudamper.mastodon.rss.logic

import java.net.URI
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
import net.matsudamper.mastodon.rss.repository.AccountRepository
import net.matsudamper.mastodon.rss.repository.Feed
import net.matsudamper.mastodon.rss.repository.FeedItemRepository
import net.matsudamper.mastodon.rss.repository.FeedItemState
import net.matsudamper.mastodon.rss.repository.FeedRepository
import net.matsudamper.mastodon.rss.repository.NewFeed
import net.matsudamper.mastodon.rss.repository.NewFeedItem
import net.matsudamper.mastodon.rss.shared.AccountId

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
    ): SaveResult {
        accounts.findById(accountId)
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
                importExistingItems(
                    feed = feed,
                    items = fetched.parsed.items,
                    feedUrl = fetched.feedUrl,
                )
                feeds.markInitialImportDone(feed.id)
                val saved = feeds.find(feed.id) ?: feed.copy(initialImportDone = true)
                SaveResult.Success(feed = saved)
            }

            FeedFetchService.FetchResult.InvalidUrl -> SaveResult.Failure(SaveFailure.INVALID_URL)

            FeedFetchService.FetchResult.TooLarge -> SaveResult.Failure(SaveFailure.FETCH_FAILED)

            is FeedFetchService.FetchResult.HttpError -> SaveResult.Failure(SaveFailure.FETCH_FAILED)

            is FeedFetchService.FetchResult.ParseError -> SaveResult.Failure(SaveFailure.PARSE_FAILED)
        }
    }

    fun findByAccountId(accountId: AccountId): Feed? = feeds.findByAccountId(accountId)

    fun unpublishedItems(accountId: AccountId): UnpublishedResult {
        accounts.findById(accountId)
            ?: return UnpublishedResult.Failure(UnpublishedFailure.UNKNOWN_ACCOUNT)
        val feed = feeds.findByAccountId(accountId)
            ?: return UnpublishedResult.Failure(UnpublishedFailure.NO_FEED)
        val items = feedItems.findPending(feed.id, Int.MAX_VALUE).map { item ->
            UnpublishedItem(
                title = item.title,
                link = item.link,
                publishedAt = item.publishedAt,
            )
        }
        return UnpublishedResult.Success(items = items)
    }

    suspend fun postUnpublished(accountId: AccountId): PostUnpublishedResult {
        val account = accounts.findById(accountId)
            ?: return PostUnpublishedResult.Failure(PostUnpublishedFailure.UNKNOWN_ACCOUNT)
        val feed = feeds.findByAccountId(accountId)
            ?: return PostUnpublishedResult.Failure(PostUnpublishedFailure.NO_FEED)
        when (val imported = importLatest(feed)) {
            is ImportLatestResult.Failure -> return PostUnpublishedResult.Failure(imported.reason)
            ImportLatestResult.Success -> Unit
        }
        val sender = actorDirectory.resolve(account.username)
            ?: return PostUnpublishedResult.Success(items = emptyList())
        val posted = mutableListOf<UnpublishedItem>()
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
            posted += UnpublishedItem(
                title = stored.title,
                link = stored.link,
                publishedAt = stored.publishedAt,
            )
        }
        return PostUnpublishedResult.Success(items = posted)
    }

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

    data class UnpublishedItem(
        val title: String?,
        val link: String?,
        val publishedAt: Instant?,
    )

    sealed interface UnpublishedResult {
        data class Success(
            val items: List<UnpublishedItem>,
        ) : UnpublishedResult

        data class Failure(
            val reason: UnpublishedFailure,
        ) : UnpublishedResult
    }

    enum class UnpublishedFailure {
        UNKNOWN_ACCOUNT,
        NO_FEED,
    }

    sealed interface PostUnpublishedResult {
        data class Success(
            val items: List<UnpublishedItem>,
        ) : PostUnpublishedResult

        data class Failure(
            val reason: PostUnpublishedFailure,
        ) : PostUnpublishedResult
    }

    enum class PostUnpublishedFailure {
        UNKNOWN_ACCOUNT,
        NO_FEED,
        INVALID_URL,
        FETCH_FAILED,
        PARSE_FAILED,
    }

    private suspend fun importLatest(feed: Feed): ImportLatestResult {
        return when (val fetched = fetcher.fetch(feed.url)) {
            is FeedFetchService.FetchResult.Success -> {
                importExistingItems(
                    feed = feed,
                    items = fetched.parsed.items,
                    feedUrl = fetched.feedUrl,
                )
                ImportLatestResult.Success
            }

            FeedFetchService.FetchResult.InvalidUrl ->
                ImportLatestResult.Failure(PostUnpublishedFailure.INVALID_URL)

            FeedFetchService.FetchResult.TooLarge,
            is FeedFetchService.FetchResult.HttpError,
            -> ImportLatestResult.Failure(PostUnpublishedFailure.FETCH_FAILED)

            is FeedFetchService.FetchResult.ParseError ->
                ImportLatestResult.Failure(PostUnpublishedFailure.PARSE_FAILED)
        }
    }

    private sealed interface ImportLatestResult {
        data object Success : ImportLatestResult

        data class Failure(
            val reason: PostUnpublishedFailure,
        ) : ImportLatestResult
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

    private fun importExistingItems(
        feed: Feed,
        items: List<ParsedFeedItem>,
        feedUrl: String,
    ) {
        val now = Instant.now()
        items.forEach { item ->
            val contentHtml = composeItemHtml(item, feedUrl)
            feedItems.add(
                NewFeedItem(
                    feedId = feed.id,
                    itemKey = FeedItemKey.of(feed.url, item).value,
                    title = item.title,
                    link = item.link,
                    contentHtml = contentHtml,
                    publishedAt = item.publishedAt ?: item.updatedAt,
                    importedAt = now,
                    state = if (contentHtml == null) FeedItemState.SKIPPED else FeedItemState.PENDING,
                ),
            )
        }
    }

    private fun composeItemHtml(
        item: ParsedFeedItem,
        feedUrl: String,
    ): String? {
        val link = resolveItemLink(item.link, feedUrl)
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

    /**
     * 記事の `link` をフィードの最終 URL 基準で絶対化する。
     *
     * Atom などは相対 IRI を許す。相対のまま `href` に入れると、受信した
     * Mastodon のホストを基準に解決され、配信元ではない場所へ飛ぶ。
     * `xml:base` はまだ見ていない。文書の取得先 URL だけを基準にする。
     */
    private fun resolveItemLink(
        link: String?,
        feedUrl: String,
    ): String {
        val trimmed = link?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return ""
        }
        return runCatching { URI(feedUrl).resolve(trimmed).toString() }.getOrDefault(trimmed)
    }

    private companion object {
        const val DEFAULT_POLL_INTERVAL_SECONDS = 900L
        const val PREVIEW_ITEM_LIMIT = 5
        const val DESCRIPTION_LIMIT = 200
        const val POST_TITLE_MAX_CHARS = 200
    }
}
