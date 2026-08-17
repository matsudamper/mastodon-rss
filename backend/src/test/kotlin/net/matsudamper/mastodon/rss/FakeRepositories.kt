package net.matsudamper.mastodon.rss

import java.time.Instant
import net.matsudamper.mastodon.rss.repository.Account
import net.matsudamper.mastodon.rss.repository.AccountRepository
import net.matsudamper.mastodon.rss.repository.FollowerRepository
import net.matsudamper.mastodon.rss.repository.IncomingFollow
import net.matsudamper.mastodon.rss.repository.NewNote
import net.matsudamper.mastodon.rss.repository.Note
import net.matsudamper.mastodon.rss.repository.NoteCursor
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

    override val followers: FollowerRepository = FakeFollowerRepository()

    override val notes: NoteRepository = FakeNoteRepository()

    override fun verifyWritable() {
        verifyWritableCallCount++
    }

    override fun close() {
        closed = true
    }
}

class FakeAccountRepository : AccountRepository {
    private val stored = mutableListOf<Account>()

    override fun list(): List<Account> = stored.toList()

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

        return Account(username = username, createdAt = createdAt).also { stored += it }
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
        followActivityUri: String,
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

    override fun list(
        username: String,
        after: NoteCursor?,
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

    override fun count(username: String): Long = stored.count { it.username == username }.toLong()
}
