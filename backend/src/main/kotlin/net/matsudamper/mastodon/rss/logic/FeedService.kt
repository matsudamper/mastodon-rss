package net.matsudamper.mastodon.rss.logic

import java.net.URI
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.matsudamper.mastodon.rss.actor.ActorDirectory
import net.matsudamper.mastodon.rss.entity.PublicNoteId as MastodonPublicNoteId
import net.matsudamper.mastodon.rss.feed.FeedFetchService
import net.matsudamper.mastodon.rss.feed.FeedItemKey
import net.matsudamper.mastodon.rss.feed.FeedText
import net.matsudamper.mastodon.rss.feed.HtmlSanitizer
import net.matsudamper.mastodon.rss.feed.HttpUrl
import net.matsudamper.mastodon.rss.feed.ParsedFeedItem
import net.matsudamper.mastodon.rss.feed.toDisplayName
import net.matsudamper.mastodon.rss.note.NotePublisher
import net.matsudamper.mastodon.rss.repository.AccountRepository
import net.matsudamper.mastodon.rss.repository.Feed
import net.matsudamper.mastodon.rss.repository.FeedFetchValidators
import net.matsudamper.mastodon.rss.repository.FeedItem
import net.matsudamper.mastodon.rss.repository.FeedItemRepository
import net.matsudamper.mastodon.rss.repository.FeedItemState
import net.matsudamper.mastodon.rss.repository.FeedRepository
import net.matsudamper.mastodon.rss.repository.NewFeed
import net.matsudamper.mastodon.rss.repository.NewFeedItem
import net.matsudamper.mastodon.rss.repository.NewNote
import net.matsudamper.mastodon.rss.repository.entity.FeedId
import net.matsudamper.mastodon.rss.repository.entity.FeedItemId
import net.matsudamper.mastodon.rss.shared.AccountId
import net.matsudamper.mastodon.rss.shared.PublicNoteId
import org.slf4j.LoggerFactory

class FeedService(
    private val accounts: AccountRepository,
    private val feeds: FeedRepository,
    private val feedItems: FeedItemRepository,
    private val fetcher: FeedFetchService,
    private val actorDirectory: ActorDirectory,
    private val notePublisher: NotePublisher,
    private val icons: FeedIcons,
) {
    private val publishLock = Mutex()

    private val logger = LoggerFactory.getLogger(FeedService::class.java)

    suspend fun preview(url: String): PreviewResult {
        return when (val fetched = fetcher.fetch(url, needsDescription = true)) {
            is FeedFetchService.FetchResult.Success -> PreviewResult.Success(fetched.toPreview())
            FeedFetchService.FetchResult.InvalidUrl -> PreviewResult.Failure(PreviewFailure.INVALID_URL)
            FeedFetchService.FetchResult.TooLarge -> PreviewResult.Failure(PreviewFailure.FETCH_FAILED)
            FeedFetchService.FetchResult.ChannelIdNotFound -> PreviewResult.Failure(PreviewFailure.FETCH_FAILED)
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
        if (existing != null && existing.initialImportDone) {
            return SaveResult.Failure(SaveFailure.ALREADY_HAS_FEED)
        }

        return when (val fetched = fetcher.fetch(url, needsDescription = false)) {
            is FeedFetchService.FetchResult.Success -> {
                val newFeed = NewFeed(
                    accountId = accountId,
                    url = fetched.feedUrl,
                    title = fetched.parsed.title,
                    siteUrl = HttpUrl.sanitize(fetched.parsed.link, fetched.feedUrl),
                    format = fetched.parsed.format.toDisplayName(),
                    iconUrl = HttpUrl.sanitize(fetched.parsed.iconUrl, fetched.feedUrl) ?: fetched.faviconUrl(),
                    pollIntervalSeconds = DEFAULT_POLL_INTERVAL_SECONDS,
                )

                // 登録は保存と完了の記録が別々に確定する。途中で終わったものは同じ URL で
                // 登録し直せないので消してやり直すが、消すのと入れるのを分けると、
                // 入らなかったときに取り込み済みの記事ごと失う
                val feed = if (existing != null) {
                    feeds.replace(existingId = existing.id, feed = newFeed)
                } else {
                    feeds.add(newFeed)
                } ?: return SaveResult.Failure(
                    // 入れ替えの失敗では自分のフィードが残っているので、
                    // フィードの有無ではなく URL の取られ方で理由を決める
                    if (feeds.findByUrl(fetched.feedUrl)?.accountId?.let { it != accountId } == true) {
                        SaveFailure.DUPLICATE_URL
                    } else {
                        SaveFailure.ALREADY_HAS_FEED
                    },
                )
                // 記録しないと、定期ポーリングが取得の時期を過ぎていると見なしてすぐ取り直す
                feeds.recordFetchSuccess(
                    id = feed.id,
                    fetchedAt = Instant.now(),
                    validators = FeedFetchValidators.NONE,
                )
                feeds.markInitialImportDone(feed.id)
                refreshIcon(feedId = feed.id, iconUrl = newFeed.iconUrl)
                val saved = feeds.find(feed.id) ?: feed.copy(initialImportDone = true)
                SaveResult.Success(feed = saved)
            }

            FeedFetchService.FetchResult.InvalidUrl -> SaveResult.Failure(SaveFailure.INVALID_URL)

            FeedFetchService.FetchResult.TooLarge -> SaveResult.Failure(SaveFailure.FETCH_FAILED)

            FeedFetchService.FetchResult.ChannelIdNotFound -> SaveResult.Failure(SaveFailure.FETCH_FAILED)

            is FeedFetchService.FetchResult.HttpError -> SaveResult.Failure(SaveFailure.FETCH_FAILED)

            is FeedFetchService.FetchResult.ParseError -> SaveResult.Failure(SaveFailure.PARSE_FAILED)
        }
    }

    fun findByAccountId(accountId: AccountId): Feed? = feeds.findByAccountId(accountId)

    fun findByAccountIds(accountIds: Set<AccountId>): Map<AccountId, Feed> =
        feeds.findByAccountIds(accountIds)

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

    /**
     * 投稿の `notes.public_id` から、その投稿の元になった記事を引く。
     *
     * 投稿の一覧に記事を並べるのに使う
     */
    fun itemsByNoteIds(noteIds: Collection<PublicNoteId>): Map<PublicNoteId, FeedItem> = feedItems.findByNoteIds(noteIds)

    /**
     * 取り込んだ記事をまとめて消す。
     *
     * 1 件でもそのアカウントのフィードに無ければ、何も消さずに NOT_FOUND を返す。
     * 一部だけ消えた状態にすると、画面から消し直す相手が分からなくなる。
     *
     * 配信した投稿は消さない。消すと次の取り込みで新着として戻ってくるので、
     * 投稿し直したい記事に使う
     */
    fun deleteItems(
        accountId: AccountId,
        feedItemIds: List<FeedItemId>,
    ): DeleteItemsResult {
        accounts.findById(accountId)
            ?: return DeleteItemsResult.Failure(DeleteItemsFailure.UNKNOWN_ACCOUNT)
        val feed = feeds.findByAccountId(accountId)
            ?: return DeleteItemsResult.Failure(DeleteItemsFailure.NO_FEED)

        // フィードを渡して、他のアカウントの記事を id だけで消せないようにする
        val targets = feedItemIds.distinct()
        if (!feedItems.delete(feedId = feed.id, ids = targets)) {
            return DeleteItemsResult.Failure(DeleteItemsFailure.NOT_FOUND)
        }
        return DeleteItemsResult.Success(deletedIds = targets)
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
        publishPending(
            feed = feed,
            username = account.username,
            htmlByKey = htmlByKey(feed = feed, items = imported.items, feedUrl = imported.feedUrl),
        )
        return PostUnpublishedResult.Success(importedCount = imported.importedCount)
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
    ): List<PollResult> = feeds.findDue(now = now, limit = limit).map { feed ->
        try {
            poll(feed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 取得の失敗は poll が結果にして返すので、ここに来るのは DB や
            // アクターの引き当てが投げた分。無人で動くので気付けるように残す
            logger.warn("フィードを処理できなかった: フィード ${feed.id.value}", e)
            PollResult(feedId = feed.id, host = feed.host(), postedItems = emptyList(), error = "処理中に例外が出た")
        }
    }

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

    /**
     * @param description 一覧に並べる用に 1 行へ潰して切り詰めた説明
     * @param fullDescription 配信元が書いたままの説明。プロフィールに取り込むときに使う
     */
    data class FeedPreview(
        val title: String?,
        val siteUrl: String?,
        val format: String,
        val description: String?,
        val fullDescription: String?,
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

    sealed interface DeleteItemsResult {
        data class Success(
            val deletedIds: List<FeedItemId>,
        ) : DeleteItemsResult

        data class Failure(
            val reason: DeleteItemsFailure,
        ) : DeleteItemsResult
    }

    enum class DeleteItemsFailure {
        UNKNOWN_ACCOUNT,
        NO_FEED,
        NOT_FOUND,
    }

    sealed interface PostUnpublishedResult {
        /**
         * @param importedCount 今回の取得で新しく取り込めた記事の件数
         */
        data class Success(
            val importedCount: Int,
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
        val fetched = when (val result = fetcher.fetch(feed.url, needsDescription = false)) {
            is FeedFetchService.FetchResult.Success -> result
            else -> return feed.recordFailure(result.failureReason())
        }

        // 取得できた時点で次の取得予定を進める。投稿の失敗で取得をやり直すと、
        // 配信元には同じ本文を配り直す理由が無いのに取りに行くことになる。
        // 条件付き GET はまだ送っていないので、保存されている値はそのまま残す
        feeds.recordFetchSuccess(id = feed.id, fetchedAt = Instant.now(), validators = feed.fetch.validators)

        val iconUrl = fetched.iconUrlOrKeep(feed)
        feeds.updateMetadata(
            id = feed.id,
            title = fetched.parsed.title,
            siteUrl = HttpUrl.sanitize(fetched.parsed.link, fetched.feedUrl),
            format = fetched.parsed.format.toDisplayName(),
            iconUrl = iconUrl,
        )
        importExistingItems(feed = feed, items = fetched.parsed.items, feedUrl = fetched.feedUrl)

        refreshIcon(feedId = feed.id, iconUrl = iconUrl)

        if (!feed.initialImportDone) {
            // 登録が途中で終わったフィード。ここで登録を終わらせる。
            // 記事は登録できたときと同じで、この後まとめて投稿する
            feeds.markInitialImportDone(feed.id)
        }

        val account = accounts.findById(feed.accountId)
            ?: return PollResult(feedId = feed.id, host = feed.host(), postedItems = emptyList(), error = "アカウントが無い")

        return PollResult(
            feedId = feed.id,
            host = feed.host(),
            postedItems = publishPending(
                feed = feed,
                username = account.username,
                htmlByKey = htmlByKey(feed = feed, items = fetched.parsed.items, feedUrl = fetched.feedUrl),
            ),
            error = null,
        )
    }

    /**
     * 記録とログに残す失敗の理由。
     *
     * 配信元から来た文字列は入れない。購読者だけが知る値を含むことがある
     */
    private fun FeedFetchService.FetchResult.failureReason(): String = when (this) {
        is FeedFetchService.FetchResult.Success -> error("成功は失敗の理由を持たない")
        FeedFetchService.FetchResult.InvalidUrl -> "URL として読めない"
        FeedFetchService.FetchResult.TooLarge -> "応答が大きすぎる"
        FeedFetchService.FetchResult.ChannelIdNotFound -> "YouTube のページからチャンネル ID を取り出せなかった"
        is FeedFetchService.FetchResult.HttpError -> status?.let { "HTTP $it" } ?: message ?: "取得に失敗した"
        is FeedFetchService.FetchResult.ParseError -> "パースに失敗した"
    }

    /**
     * 取り込めたアイコンの URL。取れていなければ今の値を残す。
     *
     * 空で上書きすると、拾えなかった 1 回でアイコンが消える。YouTube のように
     * フィード本体ではなく別のページから拾う配信元では、そのページの取得が
     * 失敗しただけでも空になる。取り込み自体は成功しているので、
     * 「名乗らなくなった」と「今回は拾えなかった」を区別できない。
     *
     * どちらも無いフィードには [faviconUrl] を充てる
     */
    private fun FeedFetchService.FetchResult.Success.iconUrlOrKeep(feed: Feed): String? =
        HttpUrl.sanitize(parsed.iconUrl, feedUrl) ?: feed.iconUrl ?: faviconUrl()

    /**
     * アイコンを名乗らないフィードに充てる、配信元のサイトの favicon。
     *
     * アイコンを表す要素を持たないフィードは多く、何も充てないとプロフィール画像が
     * 空のままになる。名乗っているものと、前に取り込んだものが両方無いときだけ使う。
     *
     * 置き場は決め打ちにして、ここではページを引かない。`<link rel="icon">` を読むには
     * 配信元が名乗った URL を引くことになり、取得先の検査を持つ IconFetchService を
     * 通さない経路が増える。実際に取れるかどうかは、そこで引いたときに決まる。
     *
     * 基準はフィードが指す Web ページ。フィードだけ別のホストで配信していることがあり、
     * フィードの URL から取ると別のサイトの favicon になる
     */
    private fun FeedFetchService.FetchResult.Success.faviconUrl(): String? {
        val siteUrl = HttpUrl.sanitize(parsed.link, feedUrl) ?: feedUrl
        return runCatching { URI(siteUrl).resolve(FAVICON_PATH).toString() }.getOrNull()
    }

    /**
     * アイコンの入れ替え。落ちても記事の取り込みは進める。
     *
     * 置き場が読めないなどで書けないことがある。アイコンが出ないだけの話なので、
     * ここで投げると記事が配られなくなるほうが困る
     */
    private suspend fun refreshIcon(
        feedId: FeedId,
        iconUrl: String?,
    ) {
        runCatching { icons.refresh(feedId = feedId, iconUrl = iconUrl) }
            .onFailure { error ->
                if (error is CancellationException) throw error
                logger.warn("アイコンを入れ替えられなかった: feedId={}", feedId.value, error)
            }
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
        FeedItemKey.of(feed.url, item).dedupeKey to composeItemHtml(item, feedUrl)
    }

    /**
     * 未投稿の記事を投稿して、投稿済みにする。
     *
     * 定期ポーリングと管理画面からの手動投稿は同時に走りうる。取り出してから
     * 投稿済みにするまでを直列化しないと、両方が同じ記事を取り出してフォロワーに
     * 2 回配信する。取り消す手段は無いので、入口を 1 本に絞って防ぐ
     *
     * 投稿は配信前に作って記事へ id を結び付ける。配信できた後、投稿済みの記録を
     * 残す前に落ちても、次は同じ id の投稿を配り直すので別投稿にはならない。
     *
     * 今回取り込んだ分に絞らず、未投稿を全部投稿する。投稿できずに残る理由は
     * 配信先の不調や停止で消えるものが多く、取り込んだ回を逃すと二度と拾えない
     */
    private suspend fun publishPending(
        feed: Feed,
        username: String,
        htmlByKey: Map<String, String?>,
    ): List<UnpublishedItem> = publishLock.withLock {
        val sender = actorDirectory.resolve(username) ?: return@withLock emptyList()
        val posted = mutableListOf<UnpublishedItem>()
        feedItems
            .findPending(feed.id, Int.MAX_VALUE)
            .forEach { stored ->
                val html = htmlByKey[stored.itemKey] ?: stored.contentHtml ?: return@forEach
                val published = try {
                    val noteId = stored.noteId ?: notePublisher
                        .create(sender = sender, contentHtml = html)
                        .let { created ->
                            val linkedNoteId = feedItems.linkNote(
                                feedId = stored.id,
                                note = NewNote(
                                    username = created.username,
                                    publicId = PublicNoteId(created.publicId.value),
                                    contentHtml = created.contentHtml,
                                    publishedAt = created.publishedAt,
                                ),
                            )
                            if (linkedNoteId.value == created.publicId.value) {
                                notePublisher.recordIfMissing(created)
                            }
                            linkedNoteId
                        }
                    when (
                        val result = notePublisher.deliver(
                            sender = sender,
                            publicId = MastodonPublicNoteId(noteId.value),
                        )
                    ) {
                        is NotePublisher.DeliverResult.Success -> result.published
                        NotePublisher.DeliverResult.NotFound -> error("記事に紐付いた投稿が見つからない")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 投稿できなかった記事は未投稿のまま残る。無人で動くので、
                    // 気付けるようにここに残す。記事のリンクや鍵は購読者だけが知る値を
                    // 含むことがあるので、こちらで採番した id だけ出す
                    logger.warn("記事を投稿できなかった: フィード ${feed.id.value} の記事 ${stored.id.value}", e)
                    return@forEach
                }
                feedItems.markPosted(stored.id, Instant.now(), noteId = PublicNoteId(published.publicId.value))
                posted += UnpublishedItem(
                    title = stored.title,
                    link = stored.link,
                    publishedAt = stored.publishedAt,
                )
            }
        posted
    }

    private suspend fun importLatest(feed: Feed): ImportLatestResult {
        return when (val fetched = fetcher.fetch(feed.url, needsDescription = false)) {
            is FeedFetchService.FetchResult.Success -> {
                val iconUrl = fetched.iconUrlOrKeep(feed)
                feeds.updateMetadata(
                    id = feed.id,
                    title = fetched.parsed.title,
                    siteUrl = HttpUrl.sanitize(fetched.parsed.link, fetched.feedUrl),
                    format = fetched.parsed.format.toDisplayName(),
                    iconUrl = iconUrl,
                )
                val importedCount = importExistingItems(
                    feed = feed,
                    items = fetched.parsed.items,
                    feedUrl = fetched.feedUrl,
                )
                refreshIcon(feedId = feed.id, iconUrl = iconUrl)
                // 記録しないと定期ポーリングが直後に取り直し、成功した後も前の失敗が残る
                feeds.recordFetchSuccess(
                    id = feed.id,
                    fetchedAt = Instant.now(),
                    validators = feed.fetch.validators,
                )
                ImportLatestResult.Success(
                    items = fetched.parsed.items,
                    feedUrl = fetched.feedUrl,
                    importedCount = importedCount,
                )
            }

            FeedFetchService.FetchResult.InvalidUrl -> {
                feed.recordFailure(fetched.failureReason())
                ImportLatestResult.Failure(PostUnpublishedFailure.INVALID_URL)
            }

            FeedFetchService.FetchResult.TooLarge,
            FeedFetchService.FetchResult.ChannelIdNotFound,
            is FeedFetchService.FetchResult.HttpError,
            -> {
                feed.recordFailure(fetched.failureReason())
                ImportLatestResult.Failure(PostUnpublishedFailure.FETCH_FAILED)
            }

            is FeedFetchService.FetchResult.ParseError -> {
                feed.recordFailure(fetched.failureReason())
                ImportLatestResult.Failure(PostUnpublishedFailure.PARSE_FAILED)
            }
        }
    }

    private sealed interface ImportLatestResult {
        data class Success(
            val items: List<ParsedFeedItem>,
            val feedUrl: String,
            val importedCount: Int,
        ) : ImportLatestResult

        data class Failure(
            val reason: PostUnpublishedFailure,
        ) : ImportLatestResult
    }

    private fun FeedFetchService.FetchResult.Success.toPreview(): FeedPreview {
        val description = parsed.description?.toPlainText()

        return FeedPreview(
            title = parsed.title,
            siteUrl = HttpUrl.sanitize(parsed.link, feedUrl),
            format = parsed.format.toDisplayName(),
            description = if (description == null) null else truncateDescription(description),
            fullDescription = description,
            itemCount = parsed.items.size,
            sampleItems = parsed.items.newestFirst().take(PREVIEW_ITEM_LIMIT).map { it.toPreviewItem() },
        )
    }

    /**
     * 記事を新しい順に並べ替える。
     *
     * `ParsedFeed.items` は XML の出現順のままで、古い順に並べる配信元もある。
     * 日時を持たない記事は判断材料が無いので、元の順のまま後ろへ送る。
     */
    private fun List<ParsedFeedItem>.newestFirst(): List<ParsedFeedItem> =
        sortedByDescending { it.publishedAt ?: it.updatedAt ?: Instant.MIN }

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
     * 取り込んだ記事を保存する。
     *
     * 既にある鍵は保存されない。同じ記事が新着として戻らないのはここで止めている
     *
     * @return 新しく保存できた記事の件数
     */
    private fun importExistingItems(
        feed: Feed,
        items: List<ParsedFeedItem>,
        feedUrl: String,
    ): Int {
        val now = Instant.now()
        return items.count { item ->
            val contentHtml = composeItemHtml(item, feedUrl)
            feedItems.add(
                NewFeedItem(
                    feedId = feed.id,
                    itemKey = FeedItemKey.of(feed.url, item).dedupeKey,
                    title = item.title,
                    link = item.link,
                    contentHtml = contentHtml,
                    publishedAt = item.publishedAt ?: item.updatedAt,
                    importedAt = now,
                    state = if (contentHtml == null) FeedItemState.SKIPPED else FeedItemState.PENDING,
                ),
            ) != null
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
        const val PREVIEW_ITEM_LIMIT = 1
        const val DESCRIPTION_LIMIT = 200
        const val POST_TITLE_MAX_CHARS = 200
        const val POST_DESCRIPTION_MAX_CHARS = 200

        /** どのサイトでも同じ場所にあることになっている favicon の置き場 */
        const val FAVICON_PATH = "/favicon.ico"
    }
}
