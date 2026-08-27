package net.matsudamper.mastodon.rss.logic

import java.net.URI
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import net.matsudamper.mastodon.rss.repository.FeedFetchValidators
import net.matsudamper.mastodon.rss.repository.FeedId
import net.matsudamper.mastodon.rss.repository.FeedItem
import net.matsudamper.mastodon.rss.repository.FeedItemId
import net.matsudamper.mastodon.rss.repository.FeedItemRepository
import net.matsudamper.mastodon.rss.repository.FeedItemState
import net.matsudamper.mastodon.rss.repository.FeedRepository
import net.matsudamper.mastodon.rss.repository.NewFeed
import net.matsudamper.mastodon.rss.repository.NewFeedItem
import net.matsudamper.mastodon.rss.shared.AccountId
import org.slf4j.LoggerFactory

class FeedService(
    private val accounts: AccountRepository,
    private val feeds: FeedRepository,
    private val feedItems: FeedItemRepository,
    private val fetcher: FeedFetchService,
    private val actorDirectory: ActorDirectory,
    private val notePublisher: NotePublisher,
) {
    private val publishLock = Mutex()

    private val logger = LoggerFactory.getLogger(FeedService::class.java)

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

        val existing = feeds.findByAccountId(accountId)
        if (existing != null) {
            if (existing.initialImportDone) {
                return SaveResult.Failure(SaveFailure.ALREADY_HAS_FEED)
            }

            // 登録は保存と取り込みが別々に確定する。途中で終わったものは定期ポーリングの
            // 対象にならず、同じ URL で登録し直すこともできなくなるので、消してやり直す
            feeds.delete(existing.id)
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
                // 記録しないと、定期ポーリングが取得の時期を過ぎていると見なしてすぐ取り直す
                feeds.recordFetchSuccess(
                    id = feed.id,
                    fetchedAt = Instant.now(),
                    validators = FeedFetchValidators.NONE,
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
        val imported = when (val result = importLatest(feed)) {
            is ImportLatestResult.Failure -> return PostUnpublishedResult.Failure(result.reason)
            is ImportLatestResult.Success -> result
        }
        val posted = publishPending(
            feed = feed,
            username = account.username,
            htmlByKey = htmlByKey(feed = feed, items = imported.items, feedUrl = imported.feedUrl),
        )
        return PostUnpublishedResult.Success(items = posted)
    }

    /**
     * 取得の時期が来たフィードを取り込み、未投稿の記事を投稿する。
     *
     * 1 本が失敗しても残りを続ける。配信元同士に関係は無いので、
     * 落ちている 1 本のせいで他のフィードが止まる方が困る。
     */
    suspend fun pollDue(
        now: Instant,
        limit: Int,
    ): List<PollResult> = feeds.findDue(now = now, limit = limit).map { feed -> poll(feed) }

    /**
     * @param host 取得先のホスト。URL には購読者だけが知るトークンが入ることがあるので、
     *   ログに出せるところまで削った形で渡す
     */
    data class PollResult(
        val feedId: FeedId,
        val host: String,
        val postedItems: List<UnpublishedItem>,
        val error: String?,
    )

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

    /**
     * 1 本のフィードを取り込んで、新着を投稿する。
     *
     * 1 回で何本も回るので、記録する時刻はここで取り直す。対象を選んだ時刻を使うと、
     * 後の方のフィードほど古い時刻が残り、間隔を待たずに取り直す
     */
    private suspend fun poll(feed: Feed): PollResult {
        val fetched = when (val result = fetcher.fetch(feed.url)) {
            is FeedFetchService.FetchResult.Success -> result

            FeedFetchService.FetchResult.InvalidUrl -> return feed.recordFailure("URL として読めない")

            FeedFetchService.FetchResult.TooLarge -> return feed.recordFailure("応答が大きすぎる")

            is FeedFetchService.FetchResult.HttpError ->
                return feed.recordFailure(result.status?.let { "HTTP $it" } ?: result.message ?: "取得に失敗した")

            is FeedFetchService.FetchResult.ParseError -> return feed.recordFailure("パースに失敗した")
        }

        // 取得できた時点で次の取得予定を進める。投稿の失敗で取得をやり直すと、
        // 配信元には同じ本文を配り直す理由が無いのに取りに行くことになる。
        // 条件付き GET はまだ送っていないので、保存されている値はそのまま残す
        feeds.recordFetchSuccess(id = feed.id, fetchedAt = Instant.now(), validators = feed.fetch.validators)

        val imported = importExistingItems(feed = feed, items = fetched.parsed.items, feedUrl = fetched.feedUrl)

        val account = accounts.findById(feed.accountId)
            ?: return PollResult(feedId = feed.id, host = feed.host(), postedItems = emptyList(), error = "アカウントが無い")

        return PollResult(
            feedId = feed.id,
            host = feed.host(),
            postedItems = publishPending(
                feed = feed,
                username = account.username,
                htmlByKey = htmlByKey(feed = feed, items = fetched.parsed.items, feedUrl = fetched.feedUrl),
                only = imported.map { it.id }.toSet(),
            ),
            error = null,
        )
    }

    private fun Feed.recordFailure(error: String): PollResult {
        feeds.recordFetchFailure(id = id, fetchedAt = Instant.now(), error = error)
        return PollResult(feedId = id, host = host(), postedItems = emptyList(), error = error)
    }

    /**
     * ログに出せるところまで削った取得先。
     *
     * URL の残りはクエリやパスに購読者だけが知るトークンを含むことがある。
     * 読めなければ空にする。ここで URL 全体に落とすと、隠す意味が無くなる
     */
    private fun Feed.host(): String = runCatching { URI(url).host }.getOrNull().orEmpty()

    /**
     * 保存済みの本文ではなく、取り込んだばかりの本文を使うための対応表。
     *
     * 配信元は同じ記事の説明を後から足すことがある。取り込んだ時点の本文だけを
     * 見ていると、その分が落ちたまま投稿される。
     */
    private fun htmlByKey(
        feed: Feed,
        items: List<ParsedFeedItem>,
        feedUrl: String,
    ): Map<String, String?> = items.associate { item ->
        FeedItemKey.of(feed.url, item).value to composeItemHtml(item, feedUrl)
    }

    /**
     * 未投稿の記事を投稿して、投稿済みにする。
     *
     * 定期ポーリングと管理画面からの手動投稿は同時に走りうる。取り出してから
     * 投稿済みにするまでを直列化しないと、両方が同じ記事を取り出してフォロワーに
     * 2 回配信する。取り消す手段は無いので、入口を 1 本に絞って防ぐ
     *
     * @param only 投稿する記事を絞る。null なら未投稿を全部投稿する。
     *   定期ポーリングは今回取り込んだ分だけを渡す。登録時に取り込んだ既存記事は
     *   確認してから手動で投稿するもので、自動では流さない
     */
    private suspend fun publishPending(
        feed: Feed,
        username: String,
        htmlByKey: Map<String, String?>,
        only: Set<FeedItemId>? = null,
    ): List<UnpublishedItem> = publishLock.withLock {
        val sender = actorDirectory.resolve(username) ?: return@withLock emptyList()
        val posted = mutableListOf<UnpublishedItem>()
        feedItems
            .findPending(feed.id, Int.MAX_VALUE)
            .filter { only == null || it.id in only }
            .forEach { stored ->
                val html = htmlByKey[stored.itemKey] ?: stored.contentHtml ?: return@forEach
                try {
                    notePublisher.publish(sender = sender, contentHtml = html)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 投稿できなかった記事は未投稿のまま残る。無人で動くので、
                    // 気付けるようにここに残す。記事のリンクや鍵は購読者だけが知る値を
                    // 含むことがあるので、こちらで採番した id だけ出す
                    logger.warn("記事を投稿できなかった: フィード ${feed.id.value} の記事 ${stored.id.value}", e)
                    return@forEach
                }
                feedItems.markPosted(stored.id, Instant.now())
                posted += UnpublishedItem(
                    title = stored.title,
                    link = stored.link,
                    publishedAt = stored.publishedAt,
                )
            }
        posted
    }

    private suspend fun importLatest(feed: Feed): ImportLatestResult {
        return when (val fetched = fetcher.fetch(feed.url)) {
            is FeedFetchService.FetchResult.Success -> {
                importExistingItems(
                    feed = feed,
                    items = fetched.parsed.items,
                    feedUrl = fetched.feedUrl,
                )
                // 記録しないと定期ポーリングが直後に取り直し、成功した後も前の失敗が残る
                feeds.recordFetchSuccess(
                    id = feed.id,
                    fetchedAt = Instant.now(),
                    validators = feed.fetch.validators,
                )
                ImportLatestResult.Success(
                    items = fetched.parsed.items,
                    feedUrl = fetched.feedUrl,
                )
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
        data class Success(
            val items: List<ParsedFeedItem>,
            val feedUrl: String,
        ) : ImportLatestResult

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

    /**
     * 取り込んだ記事を保存して、今回新しく入ったものだけを返す。
     *
     * 既にある鍵は保存されず null が返る。定期ポーリングはこの戻り値を投稿の対象にする
     */
    private fun importExistingItems(
        feed: Feed,
        items: List<ParsedFeedItem>,
        feedUrl: String,
    ): List<FeedItem> {
        val now = Instant.now()
        return items.mapNotNull { item ->
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
        val title = FeedText.singleLine(item.title.orEmpty())
        if (title.isBlank() && link.isBlank()) {
            return null
        }
        val description = item.summary
            ?.toPlainText()
            ?.let { FeedText.singleLine(it) }
            ?.let { FeedText.truncate(it, POST_DESCRIPTION_MAX_CHARS) }
            ?.takeIf { it.isNotBlank() && it != title }
            .orEmpty()
        val lines = buildList {
            if (title.isNotBlank()) {
                add(HtmlSanitizer.escapeText(FeedText.truncate(title, POST_TITLE_MAX_CHARS)))
            }
            if (description.isNotBlank()) {
                add(HtmlSanitizer.escapeText(description))
            }
            if (link.isNotBlank()) {
                val escaped = HtmlSanitizer.escapeText(link)
                add("""<a href="$escaped">$escaped</a>""")
            }
        }
        if (lines.isEmpty()) {
            return null
        }
        val sanitized = HtmlSanitizer.sanitize("<p>${lines.joinToString("<br>")}</p>")
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
        const val POST_DESCRIPTION_MAX_CHARS = 200
    }
}
