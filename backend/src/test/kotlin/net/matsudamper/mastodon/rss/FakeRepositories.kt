package net.matsudamper.mastodon.rss

import java.net.URI
import java.time.Instant
import net.matsudamper.mastodon.rss.repository.Account
import net.matsudamper.mastodon.rss.repository.AccountPosition
import net.matsudamper.mastodon.rss.repository.AccountRepository
import net.matsudamper.mastodon.rss.repository.ClaimedDelivery
import net.matsudamper.mastodon.rss.repository.DeliveryKind
import net.matsudamper.mastodon.rss.repository.DeliveryQueueCounts
import net.matsudamper.mastodon.rss.repository.DeliveryQueueRepository
import net.matsudamper.mastodon.rss.repository.EnqueueNoteResult
import net.matsudamper.mastodon.rss.repository.FailedDelivery
import net.matsudamper.mastodon.rss.repository.Feed
import net.matsudamper.mastodon.rss.repository.FeedFetchStatus
import net.matsudamper.mastodon.rss.repository.FeedFetchValidators
import net.matsudamper.mastodon.rss.repository.FeedIcon
import net.matsudamper.mastodon.rss.repository.FeedIconRepository
import net.matsudamper.mastodon.rss.repository.FeedItem
import net.matsudamper.mastodon.rss.repository.FeedItemRepository
import net.matsudamper.mastodon.rss.repository.FeedItemState
import net.matsudamper.mastodon.rss.repository.FeedRepository
import net.matsudamper.mastodon.rss.repository.FollowAcceptResult
import net.matsudamper.mastodon.rss.repository.FollowerRepository
import net.matsudamper.mastodon.rss.repository.IncomingFollow
import net.matsudamper.mastodon.rss.repository.NewFeed
import net.matsudamper.mastodon.rss.repository.NewFeedItem
import net.matsudamper.mastodon.rss.repository.NewNote
import net.matsudamper.mastodon.rss.repository.Note
import net.matsudamper.mastodon.rss.repository.NotePosition
import net.matsudamper.mastodon.rss.repository.NotePost
import net.matsudamper.mastodon.rss.repository.NoteRepository
import net.matsudamper.mastodon.rss.repository.RecordedNotePost
import net.matsudamper.mastodon.rss.repository.Repositories
import net.matsudamper.mastodon.rss.repository.RetryingDelivery
import net.matsudamper.mastodon.rss.repository.RetryingDeliveryPosition
import net.matsudamper.mastodon.rss.repository.entity.DeliveryId
import net.matsudamper.mastodon.rss.repository.entity.FeedId
import net.matsudamper.mastodon.rss.repository.entity.FeedItemId
import net.matsudamper.mastodon.rss.shared.AccountId
import net.matsudamper.mastodon.rss.shared.PublicNoteId

// ルーティングのテストで使う Repositories の差し替え。
// 保存はメモリ上だけで、DB には一切触らない。
class FakeRepositories : Repositories {
    var verifyWritableCallCount: Int = 0
        private set
    var closed: Boolean = false
        private set

    // アカウントを消すとフィードと記事も消えるのは SQLite の ON DELETE CASCADE。
    // ここで繋がないと、消したアカウントのフィード URL が埋まったままになる
    override val accounts: FakeAccountRepository = FakeAccountRepository(
        onDeleted = { accountId ->
            feeds.deleteByAccountId(accountId)?.also { feedItems.deleteByFeed(it) }
        },
    )

    override val followers: FollowerRepository = FakeFollowerRepository()

    // フィードを消すと記事も消えるのは SQLite の ON DELETE CASCADE。
    // ここで繋がないと、消したフィードの記事が残って重複判定に効いてしまう
    override val feeds: FakeFeedRepository = FakeFeedRepository(onDeleted = { feedItems.deleteByFeed(it) })

    override val feedItems: FakeFeedItemRepository = FakeFeedItemRepository()

    override val feedIcons: FakeFeedIconRepository = FakeFeedIconRepository()

    // 投稿を消したら記事の note_id が外れるのは SQLite の ON DELETE SET NULL、
    // 未配信の行が消えるのは ON DELETE CASCADE。ここで繋がないと、消した投稿の id で
    // 記事が引けたり、消した投稿の Create が送られたりする本物には無い状態になる
    override val notes: FakeNoteRepository = FakeNoteRepository(
        onDeleted = { publicId ->
            feedItems.clearNoteId(publicId)
            deliveryQueue.deleteByNote(publicId)
        },
    )

    // 投函は投稿の記録と記事の投稿済み化を一緒に書くので、両方のフェイクを繋ぐ
    override val deliveryQueue: FakeDeliveryQueueRepository = FakeDeliveryQueueRepository(notes = notes, feedItems = feedItems)

    override fun verifyWritable() {
        verifyWritableCallCount++
    }

    override fun close() {
        closed = true
    }
}

class FakeAccountRepository(
    private val onDeleted: (accountId: AccountId) -> Unit = {},
) : AccountRepository {
    private val stored = mutableListOf<Account>()
    private var nextId = 1L

    @Deprecated("ページングに移行する。list(after, limit) を使う")
    override fun list(): List<Account> = stored.toList()

    override fun list(after: AccountPosition?, limit: Int): List<Account> {
        if (limit <= 0) return listOf()
        val sorted = stored.sortedWith(compareBy({ it.createdAt }, { it.id.value }))
        val laterThanAfter = if (after == null) {
            sorted
        } else {
            sorted.filter { it.createdAt > after.createdAt || (it.createdAt == after.createdAt && it.id.value > after.id.value) }
        }
        return laterThanAfter.take(limit)
    }

    override fun findById(id: AccountId): Account? = stored.firstOrNull { it.id == id }

    override fun findByUsername(username: String): Account? = stored.firstOrNull { it.username.equals(username, ignoreCase = true) }

    override fun findByUsernames(usernames: Collection<String>): Map<String, Account> =
        usernames.mapNotNull { username ->
            val account = findByUsername(username) ?: return@mapNotNull null
            username to account
        }.toMap()

    override fun add(
        username: String,
        createdAt: Instant,
    ): Account? {
        if (findByUsername(username) != null) return null

        return Account(
            id = AccountId(nextId++),
            username = username,
            createdAt = createdAt,
            displayName = null,
            summary = null,
        ).also { stored += it }
    }

    override fun updateProfile(
        id: AccountId,
        displayName: String?,
        summary: String?,
    ): Account? {
        val index = stored.indexOfFirst { it.id == id }
        if (index == -1) return null

        val updated = stored[index].copy(displayName = displayName, summary = summary)
        stored[index] = updated
        return updated
    }

    override fun delete(id: AccountId): Boolean {
        if (!stored.removeAll { it.id == id }) return false
        onDeleted(id)
        return true
    }
}

/**
 * 記録するだけの [FollowerRepository]。ルーティングのテストでは中身を見ない
 */
class FakeFollowerRepository : FollowerRepository {
    private val stored = mutableListOf<IncomingFollow>()

    override fun record(follow: IncomingFollow) {
        if (stored.none { it.username == follow.username && it.follower.actorUri == follow.follower.actorUri }) {
            stored += follow
        }
    }

    override fun markAccepted(
        username: String,
        followerActorUri: String,
        acceptedAt: Instant,
    ): FollowAcceptResult = when {
        stored.none { it.username == username && it.follower.actorUri == followerActorUri } -> FollowAcceptResult.NotFound
        accepted.add(username to followerActorUri) -> FollowAcceptResult.FirstAccept
        else -> FollowAcceptResult.AlreadyAccepted
    }

    override fun remove(
        username: String,
        followerActorUri: String,
        followActivityUri: String?,
    ): Boolean = stored.removeAll { it.username == username && it.follower.actorUri == followerActorUri }

    override fun removeAccount(username: String): Int {
        val before = stored.size
        stored.removeAll { it.username.equals(username, ignoreCase = true) }
        // 行ごと消える本物と揃える。残すと、同じ名前で作り直した後の Follow が
        // Accept を返す前から受理済みとして数えられる
        accepted.removeAll { (acceptedUsername, _) -> acceptedUsername.equals(username, ignoreCase = true) }
        return before - stored.size
    }

    override fun removeRemoteActor(actorUri: String): Int {
        val before = stored.size
        stored.removeAll { it.follower.actorUri == actorUri }
        return before - stored.size
    }

    override fun list(
        username: String,
        after: String?,
        limit: Int,
    ): List<String> = acceptedFollowers(username)
        .sorted()
        .filter { after == null || it > after }
        .take(limit)

    override fun count(username: String): Long = acceptedFollowers(username).size.toLong()

    override fun counts(usernames: Set<String>): Map<String, Long> = usernames.associateWith { count(it) }

    override fun deliveryTargets(username: String): List<String> = stored
        .filter { it.username == username && (username to it.follower.actorUri) in accepted }
        .map { it.follower.sharedInbox ?: it.follower.inbox }
        .distinct()

    override fun hasAny(): Boolean = stored.isNotEmpty()

    private val accepted = mutableSetOf<Pair<String, String>>()

    private fun acceptedFollowers(username: String): List<String> = stored
        .filter { it.username == username && (username to it.follower.actorUri) in accepted }
        .map { it.follower.actorUri }
}

/**
 * 記録するだけの [NoteRepository]
 */
class FakeNoteRepository(
    private val onDeleted: (publicId: PublicNoteId) -> Unit = {},
) : NoteRepository {
    private val stored = mutableListOf<Note>()

    override fun add(note: NewNote) {
        stored += Note(
            publicId = note.publicId,
            username = note.username,
            contentHtml = note.contentHtml,
            publishedAt = note.publishedAt,
        )
    }

    override fun find(publicId: PublicNoteId): Note? = stored.firstOrNull { it.publicId == publicId }

    override fun findByPublicIds(publicIds: Set<PublicNoteId>): Map<PublicNoteId, Note> = stored
        .filter { it.publicId in publicIds }
        .associateBy { it.publicId }

    override fun delete(publicId: PublicNoteId) {
        if (stored.removeAll { it.publicId == publicId }) {
            onDeleted(publicId)
        }
    }

    override fun deleteByUsername(username: String): Int {
        val targets = stored.filter { it.username.equals(username, ignoreCase = true) }
        stored.removeAll(targets)
        targets.forEach { onDeleted(it.publicId) }
        return targets.size
    }

    override fun list(
        username: String,
        after: NotePosition?,
        limit: Int,
    ): List<Note> = stored
        .filter { it.username == username }
        .sortedWith(compareByDescending<Note> { it.publishedAt }.thenByDescending { it.publicId.value })
        .filter { note ->
            after == null ||
                note.publishedAt < after.publishedAt ||
                (note.publishedAt == after.publishedAt && note.publicId.value < after.publicId.value)
        }
        .take(limit)

    override fun listPositions(
        username: String,
        after: NotePosition?,
        limit: Int,
    ): List<NotePosition> = list(username = username, after = after, limit = limit)
        .map { NotePosition(publishedAt = it.publishedAt, publicId = it.publicId) }

    override fun count(username: String): Long = stored.count { it.username == username }.toLong()

    override fun counts(usernames: Set<String>): Map<String, Long> =
        usernames.associateWith { count(it) }

    /**
     * 記録した順に全部返す。一覧は新しい順で、同じ時刻の並びが id 次第になるので、
     * 投稿した順を確かめるテストはこちらを見る
     */
    fun all(): List<Note> = stored.toList()
}

class FakeFeedRepository(
    private val onDeleted: (feedId: FeedId) -> Unit = {},
) : FeedRepository {
    private val stored = mutableListOf<Feed>()
    private var nextId = 1L

    override fun list(): List<Feed> = stored.toList()

    override fun find(id: FeedId): Feed? = stored.firstOrNull { it.id == id }

    override fun findByAccountId(accountId: AccountId): Feed? = stored.firstOrNull { it.accountId == accountId }

    override fun findByAccountIds(accountIds: Set<AccountId>): Map<AccountId, Feed> =
        stored.filter { it.accountId in accountIds }.associateBy { it.accountId }

    override fun findByUrl(url: String): Feed? = stored.firstOrNull { it.url == url }

    override fun findDue(
        now: Instant,
        limit: Int,
    ): List<Feed> =
        stored
            .filter {
                val lastFetchedAt = it.fetch.lastFetchedAt
                val due = lastFetchedAt == null || lastFetchedAt.plusSeconds(it.pollIntervalSeconds) <= now
                val registrationTimedOut = it.createdAt.plusSeconds(it.pollIntervalSeconds) <= now
                due && (it.initialImportDone || registrationTimedOut)
            }
            .sortedBy { it.fetch.lastFetchedAt ?: Instant.MIN }
            .take(limit)

    override fun add(feed: NewFeed): Feed? {
        if (findByAccountId(feed.accountId) != null) return null
        if (findByUrl(feed.url) != null) return null

        val createdAt = Instant.now()
        return Feed(
            id = FeedId(nextId++),
            accountId = feed.accountId,
            url = feed.url,
            title = feed.title,
            siteUrl = feed.siteUrl,
            format = feed.format,
            iconUrl = feed.iconUrl,
            pollIntervalSeconds = feed.pollIntervalSeconds,
            fetch = FeedFetchStatus(
                validators = FeedFetchValidators.NONE,
                lastFetchedAt = null,
                lastSucceededAt = null,
                lastError = null,
            ),
            initialImportDone = false,
            createdAt = createdAt,
        ).also { stored += it }
    }

    override fun replace(
        existingId: FeedId,
        feed: NewFeed,
    ): Feed? {
        val existing = find(existingId) ?: return null
        if (existing.initialImportDone) return null
        if (findByAccountId(feed.accountId)?.id?.let { it != existingId } == true) return null
        if (findByUrl(feed.url)?.id?.let { it != existingId } == true) return null

        delete(existingId)
        return add(feed)
    }

    override fun updateMetadata(
        id: FeedId,
        title: String?,
        siteUrl: String?,
        format: String?,
        iconUrl: String?,
    ) {
        update(id) { it.copy(title = title, siteUrl = siteUrl, format = format, iconUrl = iconUrl) }
    }

    override fun recordFetchSuccess(
        id: FeedId,
        fetchedAt: Instant,
        validators: FeedFetchValidators,
    ) {
        update(id) {
            it.copy(
                fetch = FeedFetchStatus(
                    validators = validators,
                    lastFetchedAt = fetchedAt,
                    lastSucceededAt = fetchedAt,
                    lastError = null,
                ),
            )
        }
    }

    override fun recordFetchFailure(
        id: FeedId,
        fetchedAt: Instant,
        error: String,
    ) {
        update(id) {
            it.copy(
                fetch = it.fetch.copy(
                    lastFetchedAt = fetchedAt,
                    lastError = error,
                ),
            )
        }
    }

    override fun markInitialImportDone(id: FeedId) {
        update(id) { it.copy(initialImportDone = true) }
    }

    /** 登録の取り込みが終わっていない状態を作る。本物には無い、テストのための口 */
    fun clearInitialImportDone(id: FeedId) {
        update(id) { it.copy(initialImportDone = false) }
    }

    override fun delete(id: FeedId) {
        if (stored.removeAll { it.id == id }) {
            onDeleted(id)
        }
    }

    /**
     * @return 消したフィードの id。そのアカウントにフィードが無ければ null
     */
    fun deleteByAccountId(accountId: AccountId): FeedId? {
        val feed = findByAccountId(accountId) ?: return null
        stored.remove(feed)
        return feed.id
    }

    private fun update(
        id: FeedId,
        block: (Feed) -> Feed,
    ) {
        val index = stored.indexOfFirst { it.id == id }
        if (index == -1) return
        stored[index] = block(stored[index])
    }
}

class FakeFeedItemRepository : FeedItemRepository {
    private val stored = mutableListOf<FeedItem>()
    private var nextId = 1L

    override fun findExistingKeys(
        feedId: FeedId,
        keys: Collection<String>,
    ): Set<String> {
        if (keys.isEmpty()) return emptySet()
        val wanted = keys.toSet()
        return stored.filter { it.feedId == feedId && it.itemKey in wanted }.map { it.itemKey }.toSet()
    }

    override fun add(item: NewFeedItem): FeedItem? {
        if (stored.any { it.feedId == item.feedId && it.itemKey == item.itemKey }) return null

        return FeedItem(
            id = FeedItemId(nextId++),
            feedId = item.feedId,
            itemKey = item.itemKey,
            title = item.title,
            link = item.link,
            contentHtml = item.contentHtml,
            publishedAt = item.publishedAt,
            importedAt = item.importedAt,
            state = item.state,
            postedAt = null,
            noteId = null,
        ).also { stored += it }
    }

    override fun findPending(limit: Int): List<FeedItem> = pendingSorted().take(limit.coerceAtLeast(0))

    override fun findPending(
        feedId: FeedId,
        limit: Int,
    ): List<FeedItem> = pendingSorted().filter { it.feedId == feedId }.take(limit.coerceAtLeast(0))

    override fun markPosted(
        id: FeedItemId,
        postedAt: Instant,
        noteId: PublicNoteId,
    ) {
        update(id) { it.copy(state = FeedItemState.POSTED, postedAt = postedAt, noteId = noteId) }
    }

    override fun markSkipped(id: FeedItemId) {
        update(id) { it.copy(state = FeedItemState.SKIPPED) }
    }

    override fun findByNoteIds(noteIds: Collection<PublicNoteId>): Map<PublicNoteId, FeedItem> {
        if (noteIds.isEmpty()) return emptyMap()
        val wanted = noteIds.toSet()
        return stored.filter { it.noteId in wanted }.associateBy { checkNotNull(it.noteId) }
    }

    override fun find(id: FeedItemId): FeedItem? = stored.firstOrNull { it.id == id }

    override fun delete(
        feedId: FeedId,
        ids: Collection<FeedItemId>,
    ): Boolean {
        val targets = ids.toSet()
        if (targets.any { id -> stored.none { it.id == id && it.feedId == feedId } }) return false

        stored.removeAll { it.id in targets }
        return true
    }

    override fun countByFeed(feedId: FeedId): Long = stored.count { it.feedId == feedId }.toLong()

    fun items(): List<FeedItem> = stored.toList()

    fun deleteByFeed(feedId: FeedId) {
        stored.removeAll { it.feedId == feedId }
    }

    /**
     * 投稿を紐付けたまま未投稿に戻す。配信の直前に紐付けていた頃の版が残す状態を作る
     */
    fun backToPending(id: FeedItemId) {
        update(id) { it.copy(state = FeedItemState.PENDING, postedAt = null) }
    }

    fun clearNoteId(noteId: PublicNoteId) {
        stored.replaceAll { item -> if (item.noteId == noteId) item.copy(noteId = null) else item }
    }

    private fun pendingSorted(): List<FeedItem> =
        stored
            .filter { it.state == FeedItemState.PENDING }
            .sortedWith(
                compareBy<FeedItem> { it.publishedAt == null }
                    .thenBy { it.publishedAt }
                    .thenBy { it.id.value },
            )

    private fun update(
        id: FeedItemId,
        block: (FeedItem) -> FeedItem,
    ) {
        val index = stored.indexOfFirst { it.id == id }
        if (index == -1) return
        stored[index] = block(stored[index])
    }
}

/**
 * 配信キューの差し替え。オンメモリで持つ。
 *
 * 投函は本物と同じく、投稿の記録と記事の投稿済み化を一緒に書く。
 * SQL の振る舞いは `:backend:repository` のテストが本物の SQLite で確かめる
 */
class FakeDeliveryQueueRepository(
    private val notes: FakeNoteRepository,
    private val feedItems: FakeFeedItemRepository,
) : DeliveryQueueRepository {
    private val stored = mutableListOf<Row>()
    private var nextId = 1L

    override fun enqueueNote(post: NotePost): EnqueueNoteResult {
        val feedItemId = post.feedItemId
        if (feedItemId != null) {
            val item = feedItems.find(feedItemId)
            if (item == null || item.state != FeedItemState.PENDING) return EnqueueNoteResult.FeedItemNotPending
            feedItems.markPosted(feedItemId, postedAt = post.enqueuedAt, noteId = post.note.publicId)
        }
        notes.add(post.note)
        post.inboxes.forEach { inbox ->
            stored += Row(
                id = DeliveryId(nextId++),
                notePublicId = post.note.publicId,
                username = post.note.username,
                inbox = inbox,
                body = post.body,
                state = State.PENDING,
                attempts = 0,
                nextAttemptAt = post.enqueuedAt,
                enqueuedAt = post.enqueuedAt,
                lastError = null,
            )
        }
        return EnqueueNoteResult.Queued(deliveries = post.inboxes.size)
    }

    override fun requeueNote(post: RecordedNotePost): EnqueueNoteResult {
        val item = feedItems.find(post.feedItemId)
        if (item == null || item.state != FeedItemState.PENDING) return EnqueueNoteResult.FeedItemNotPending
        feedItems.markPosted(post.feedItemId, postedAt = post.enqueuedAt, noteId = post.publicId)
        post.inboxes.forEach { inbox ->
            stored += Row(
                id = DeliveryId(nextId++),
                notePublicId = post.publicId,
                username = post.username,
                inbox = inbox,
                body = post.body,
                state = State.PENDING,
                attempts = 0,
                nextAttemptAt = post.enqueuedAt,
                enqueuedAt = post.enqueuedAt,
                lastError = null,
            )
        }
        return EnqueueNoteResult.Queued(deliveries = post.inboxes.size)
    }

    override fun claim(
        now: Instant,
        limit: Int,
    ): List<ClaimedDelivery> {
        val claimed = stored
            .filter { it.state == State.PENDING && it.nextAttemptAt != null && !it.nextAttemptAt.isAfter(now) }
            .sortedWith(compareBy<Row> { it.nextAttemptAt }.thenBy { it.id.value })
            .distinctBy { hostOf(it.inbox) }
            .take(limit.coerceAtLeast(0))
        claimed.forEach { row -> update(row.id) { it.copy(state = State.DELIVERING, attempts = it.attempts + 1) } }
        return claimed.map { row ->
            val current = checkNotNull(find(row.id))
            ClaimedDelivery(
                id = current.id,
                kind = DeliveryKind.CREATE_NOTE,
                username = current.username,
                inbox = current.inbox,
                body = checkNotNull(current.body),
                attempts = current.attempts,
                enqueuedAt = current.enqueuedAt,
            )
        }
    }

    private fun hostOf(inbox: String): String = runCatching { URI(inbox).host }.getOrNull() ?: inbox

    override fun exists(id: DeliveryId): Boolean = find(id) != null

    override fun markDelivered(id: DeliveryId) {
        stored.removeAll { it.id == id }
    }

    override fun scheduleRetry(
        id: DeliveryId,
        nextAttemptAt: Instant,
        error: String,
    ) {
        update(id) { it.copy(state = State.PENDING, nextAttemptAt = nextAttemptAt, lastError = error) }
    }

    override fun giveUp(
        id: DeliveryId,
        error: String,
    ) {
        update(id) { it.copy(state = State.FAILED, nextAttemptAt = null, body = null, lastError = error) }
    }

    override fun recoverDelivering(): Int {
        val targets = stored.filter { it.state == State.DELIVERING }
        targets.forEach { row -> update(row.id) { it.copy(state = State.PENDING) } }
        return targets.size
    }

    override fun counts(username: String): DeliveryQueueCounts {
        val mine = stored.filter { it.username.equals(username, ignoreCase = true) }
        return DeliveryQueueCounts(
            waiting = mine.count { it.state != State.FAILED }.toLong(),
            failed = mine.count { it.state == State.FAILED }.toLong(),
        )
    }

    override fun listRetrying(
        username: String,
        after: RetryingDeliveryPosition?,
        limit: Int,
    ): List<RetryingDelivery> = stored
        .filter { it.username.equals(username, ignoreCase = true) && it.state == State.PENDING && it.attempts > 0 }
        .map { row ->
            RetryingDelivery(
                id = row.id,
                inbox = row.inbox,
                attempts = row.attempts,
                nextAttemptAt = checkNotNull(row.nextAttemptAt),
                lastError = row.lastError,
            )
        }
        .sortedWith(compareBy<RetryingDelivery> { it.nextAttemptAt }.thenBy { it.id.value })
        .filter { delivery ->
            after == null ||
                delivery.nextAttemptAt > after.nextAttemptAt ||
                (delivery.nextAttemptAt == after.nextAttemptAt && delivery.id.value > after.id.value)
        }
        .take(limit.coerceAtLeast(0))

    override fun listFailed(
        username: String,
        afterId: DeliveryId?,
        limit: Int,
    ): List<FailedDelivery> = stored
        .filter { it.username.equals(username, ignoreCase = true) && it.state == State.FAILED }
        .sortedByDescending { it.id.value }
        .filter { afterId == null || it.id.value < afterId.value }
        .map { row -> FailedDelivery(id = row.id, inbox = row.inbox, attempts = row.attempts, lastError = row.lastError) }
        .take(limit.coerceAtLeast(0))

    override fun deleteByUsername(username: String): Int {
        val before = stored.size
        stored.removeAll { it.username.equals(username, ignoreCase = true) }
        return before - stored.size
    }

    fun rows(): List<Row> = stored.toList()

    fun deleteByNote(publicId: PublicNoteId) {
        stored.removeAll { it.notePublicId == publicId }
    }

    private fun find(id: DeliveryId): Row? = stored.firstOrNull { it.id == id }

    private fun update(
        id: DeliveryId,
        block: (Row) -> Row,
    ) {
        val index = stored.indexOfFirst { it.id == id }
        if (index == -1) return
        stored[index] = block(stored[index])
    }

    enum class State {
        PENDING,
        DELIVERING,
        FAILED,
    }

    data class Row(
        val id: DeliveryId,
        val notePublicId: PublicNoteId,
        val username: String,
        val inbox: String,
        val body: String?,
        val state: State,
        val attempts: Int,
        val nextAttemptAt: Instant?,
        val enqueuedAt: Instant,
        val lastError: String?,
    )
}

class FakeFeedIconRepository : FeedIconRepository {
    private val stored = mutableMapOf<FeedId, FeedIcon>()

    override fun find(feedId: FeedId): FeedIcon? = stored[feedId]

    override fun save(
        feedId: FeedId,
        icon: FeedIcon,
    ) {
        stored[feedId] = icon
    }

    override fun delete(feedId: FeedId) {
        stored.remove(feedId)
    }
}
