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
    override suspend fun usage(): NodeInfoUsage {
        val usernames = accounts.list().map { it.username }.toSet()

        return NodeInfoUsage(
            users = NodeInfoUsers(total = usernames.size.toLong()),
            localPosts = notes.counts(usernames).values.sum(),
        )
    }
}
