package net.matsudamper.mastodon.rss

import java.time.Instant
import net.matsudamper.mastodon.rss.repository.Account
import net.matsudamper.mastodon.rss.repository.AccountId
import net.matsudamper.mastodon.rss.repository.AccountProfile
import net.matsudamper.mastodon.rss.repository.AccountProfileRepository
import net.matsudamper.mastodon.rss.repository.AccountRepository
import net.matsudamper.mastodon.rss.repository.Feed
import net.matsudamper.mastodon.rss.repository.FeedFetchStatus
import net.matsudamper.mastodon.rss.repository.FeedFetchValidators
import net.matsudamper.mastodon.rss.repository.FeedId
import net.matsudamper.mastodon.rss.repository.FeedRepository
import net.matsudamper.mastodon.rss.repository.FollowerRepository
import net.matsudamper.mastodon.rss.repository.IncomingFollow
import net.matsudamper.mastodon.rss.repository.NewFeed
import net.matsudamper.mastodon.rss.repository.NewNote
import net.matsudamper.mastodon.rss.repository.Note
import net.matsudamper.mastodon.rss.repository.NotePosition
import net.matsudamper.mastodon.rss.repository.NoteRepository
import net.matsudamper.mastodon.rss.repository.Repositories

// ルーティングのテストで使う Repositories の差し替え。
// 保存はメモリ上だけで、DB には一切触らない。
class FakeRepositories : Repositories {
    var verifyWritableCallCount: Int = 0
        private set
    var closed: Boolean = false
        private set

    override val accounts: FakeAccountRepository = FakeAccountRepository()

    override val accountProfiles: FakeAccountProfileRepository = FakeAccountProfileRepository()

    override val followers: FollowerRepository = FakeFollowerRepository()

    override val notes: NoteRepository = FakeNoteRepository()

    override val feeds: FakeFeedRepository = FakeFeedRepository()

    override fun verifyWritable() {
        verifyWritableCallCount++
    }

    override fun close() {
        closed = true
    }
}

class FakeAccountRepository : AccountRepository {
    private val stored = mutableListOf<Account>()
    private var nextId = 1L

    @Deprecated("ページングに移行する。list(afterUsername, limit) を使う")
    override fun list(): List<Account> = stored.toList()

    override fun list(afterUsername: String?, limit: Int): List<Account> {
        if (limit <= 0) return emptyList()
        val startIndex = if (afterUsername != null) {
            val idx = stored.indexOfFirst { it.username.equals(afterUsername, ignoreCase = true) }
            if (idx == -1) return emptyList()
            idx + 1
        } else {
            0
        }
        return stored.drop(startIndex).take(limit)
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

        return Account(id = AccountId(nextId++), username = username, createdAt = createdAt).also { stored += it }
    }
}

class FakeAccountProfileRepository : AccountProfileRepository {
    private val stored = mutableMapOf<String, AccountProfile>()

    override fun findByUsername(username: String): AccountProfile? = stored[username.lowercase()]

    override fun findByUsernames(usernames: Collection<String>): Map<String, AccountProfile> =
        usernames.mapNotNull { username ->
            val profile = findByUsername(username) ?: return@mapNotNull null
            username to profile
        }.toMap()

    override fun upsert(
        username: String,
        displayName: String,
        summary: String,
    ): AccountProfile = AccountProfile(
        username = username,
        displayName = displayName,
        summary = summary,
    ).also { stored[username.lowercase()] = it }
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
    ): Boolean = accepted.add(username to followerActorUri)

    override fun remove(
        username: String,
        followerActorUri: String,
        followActivityUri: String?,
    ): Boolean = stored.removeAll { it.username == username && it.follower.actorUri == followerActorUri }

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
class FakeNoteRepository : NoteRepository {
    private val stored = mutableListOf<Note>()

    override fun add(note: NewNote) {
        stored += Note(
            publicId = note.publicId,
            username = note.username,
            contentHtml = note.contentHtml,
            publishedAt = note.publishedAt,
        )
    }

    override fun find(publicId: String): Note? = stored.firstOrNull { it.publicId == publicId }

    override fun findByPublicIds(publicIds: Set<String>): Map<String, Note> = stored
        .filter { it.publicId in publicIds }
        .associateBy { it.publicId }

    override fun list(
        username: String,
        after: NotePosition?,
        limit: Int,
    ): List<Note> = stored
        .filter { it.username == username }
        .sortedWith(compareByDescending<Note> { it.publishedAt }.thenByDescending { it.publicId })
        .filter { note ->
            after == null ||
                note.publishedAt < after.publishedAt ||
                (note.publishedAt == after.publishedAt && note.publicId < after.publicId)
        }
        .take(limit)

    override fun listPositions(
        username: String,
        after: NotePosition?,
        limit: Int,
    ): List<NotePosition> = list(username = username, after = after, limit = limit)
        .map { NotePosition(publishedAt = it.publishedAt, publicId = it.publicId) }

    override fun count(username: String): Long = stored.count { it.username == username }.toLong()
}

class FakeFeedRepository : FeedRepository {
    private val stored = mutableListOf<Feed>()
    private var nextId = 1L

    override fun list(): List<Feed> = stored.toList()

    override fun find(id: FeedId): Feed? = stored.firstOrNull { it.id == id }

    override fun findByAccountId(accountId: AccountId): Feed? = stored.firstOrNull { it.accountId == accountId }

    override fun findByUrl(url: String): Feed? = stored.firstOrNull { it.url == url }

    override fun findDue(
        now: Instant,
        limit: Int,
    ): List<Feed> =
        stored
            .filter {
                val lastFetchedAt = it.fetch.lastFetchedAt
                lastFetchedAt == null || lastFetchedAt.plusSeconds(it.pollIntervalSeconds) <= now
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

    override fun updateMetadata(
        id: FeedId,
        title: String?,
        siteUrl: String?,
        format: String?,
    ) {
        update(id) { it.copy(title = title, siteUrl = siteUrl, format = format) }
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

    override fun delete(id: FeedId) {
        stored.removeAll { it.id == id }
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
