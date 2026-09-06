package net.matsudamper.mastodon.rss.graphql

import net.matsudamper.mastodon.rss.actor.ActorDirectory
import net.matsudamper.mastodon.rss.actor.ActorPublisher
import net.matsudamper.mastodon.rss.crypto.PasswordHash
import net.matsudamper.mastodon.rss.feed.FeedFetchService
import net.matsudamper.mastodon.rss.logic.AccountService
import net.matsudamper.mastodon.rss.logic.AdminLoginService
import net.matsudamper.mastodon.rss.logic.FeedService
import net.matsudamper.mastodon.rss.logic.NoteService
import net.matsudamper.mastodon.rss.note.NotePublisher
import net.matsudamper.mastodon.rss.note.NoteStore
import net.matsudamper.mastodon.rss.repository.AccountRepository
import net.matsudamper.mastodon.rss.repository.FeedItemRepository
import net.matsudamper.mastodon.rss.repository.FeedRepository
import net.matsudamper.mastodon.rss.repository.FollowerRepository

class DiContainer(
    passwordHash: PasswordHash?,
    accountRepository: AccountRepository,
    followerRepository: FollowerRepository,
    feedRepository: FeedRepository,
    feedItemRepository: FeedItemRepository,
    feedFetcher: FeedFetchService,
    val domain: String,
    val actorDirectory: ActorDirectory,
    notePublisher: NotePublisher,
    actorPublisher: ActorPublisher,
    val noteStore: NoteStore,
) {
    val adminLoginService: AdminLoginService = AdminLoginService(passwordHash)

    val accountService: AccountService = AccountService(
        accounts = accountRepository,
        followers = followerRepository,
        actorPublisher = actorPublisher,
        domain = domain,
    )

    val noteService: NoteService = NoteService(
        directory = actorDirectory,
        publisher = notePublisher,
        notes = noteStore,
    )

    val feedService: FeedService = FeedService(
        accounts = accountRepository,
        feeds = feedRepository,
        feedItems = feedItemRepository,
        fetcher = feedFetcher,
        actorDirectory = actorDirectory,
        notePublisher = notePublisher,
    )
}
