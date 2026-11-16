package net.matsudamper.mastodon.rss.nodeinfo

import net.matsudamper.kotpub.nodeinfo.NodeInfoUsage
import net.matsudamper.kotpub.nodeinfo.NodeInfoUsageSource
import net.matsudamper.kotpub.nodeinfo.NodeInfoUsers
import net.matsudamper.mastodon.rss.repository.AccountRepository
import net.matsudamper.mastodon.rss.repository.NoteRepository

internal class RepositoryNodeInfoUsage(
    private val accounts: AccountRepository,
    private val notes: NoteRepository,
) : NodeInfoUsageSource {
    override suspend fun usage(): NodeInfoUsage =
        generateSequence(accounts.list(after = null, limit = PAGE_SIZE)) { previousPage ->
            previousPage.lastOrNull()?.let { accounts.list(after = it.position(), limit = PAGE_SIZE) }
        }.takeWhile { it.isNotEmpty() }
            .map { page -> page.map { it.username }.toSet() }
            .fold(NodeInfoUsage(users = NodeInfoUsers(total = 0), localPosts = 0)) { counted, usernames ->
                NodeInfoUsage(
                    users = NodeInfoUsers(total = counted.users.total + usernames.size),
                    localPosts = counted.localPosts + notes.counts(usernames).values.sum(),
                )
            }

    private companion object {
        const val PAGE_SIZE = 100
    }
}
