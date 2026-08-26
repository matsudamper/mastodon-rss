package net.matsudamper.mastodon.rss.graphql

import net.matsudamper.mastodon.rss.actor.ActorDirectory
import net.matsudamper.mastodon.rss.crypto.PasswordHash
import net.matsudamper.mastodon.rss.logic.AccountService
import net.matsudamper.mastodon.rss.logic.AdminLoginService
import net.matsudamper.mastodon.rss.logic.FeedService
import net.matsudamper.mastodon.rss.logic.NoteService
import net.matsudamper.mastodon.rss.note.NotePublisher
import net.matsudamper.mastodon.rss.note.NoteStore
import net.matsudamper.mastodon.rss.repository.AccountRepository
import net.matsudamper.mastodon.rss.repository.FollowerRepository

class DiContainer(
    passwordHash: PasswordHash?,
    accountRepository: AccountRepository,
    followerRepository: FollowerRepository,
    val domain: String,
    val actorDirectory: ActorDirectory,
    notePublisher: NotePublisher,
    val noteStore: NoteStore,
    val feedService: FeedService,
) {
    val adminLoginService: AdminLoginService = AdminLoginService(passwordHash)

    val accountService: AccountService = AccountService(
        accounts = accountRepository,
        followers = followerRepository,
        domain = domain,
    )

    val noteService: NoteService = NoteService(
        directory = actorDirectory,
        publisher = notePublisher,
        notes = noteStore,
    )
}
