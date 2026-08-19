package net.matsudamper.mastodon.rss

import java.time.Instant
import net.matsudamper.mastodon.rss.repository.Account
import net.matsudamper.mastodon.rss.repository.AccountRepository
import net.matsudamper.mastodon.rss.repository.Repositories

// ルーティングのテストで使う Repositories の差し替え。
// 保存はメモリ上だけで、DB には一切触らない。
class FakeRepositories : Repositories {
    var verifyWritableCallCount: Int = 0
        private set
    var closed: Boolean = false
        private set

    override val accounts: FakeAccountRepository = FakeAccountRepository()

    override fun verifyWritable() {
        verifyWritableCallCount++
    }

    override fun close() {
        closed = true
    }
}

class FakeAccountRepository : AccountRepository {
    private val stored = mutableListOf<Account>()

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
