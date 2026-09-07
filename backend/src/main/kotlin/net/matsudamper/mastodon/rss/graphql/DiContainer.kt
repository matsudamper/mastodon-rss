package net.matsudamper.mastodon.rss.graphql

import net.matsudamper.mastodon.rss.actor.ActorDirectory
import net.matsudamper.mastodon.rss.actor.ActorPublisher
import net.matsudamper.mastodon.rss.crypto.PasswordHash
import net.matsudamper.mastodon.rss.logic.AccountService
import net.matsudamper.mastodon.rss.logic.AdminLoginService
import net.matsudamper.mastodon.rss.logic.DeliveryQueueService
import net.matsudamper.mastodon.rss.logic.FeedService
import net.matsudamper.mastodon.rss.logic.NotePoster
import net.matsudamper.mastodon.rss.logic.NoteService
import net.matsudamper.mastodon.rss.note.NotePublisher
import net.matsudamper.mastodon.rss.note.NoteStore
import net.matsudamper.mastodon.rss.repository.AccountRepository
import net.matsudamper.mastodon.rss.repository.DeliveryQueueRepository
import net.matsudamper.mastodon.rss.repository.FollowerRepository

class DiContainer(
    passwordHash: PasswordHash?,
    accountRepository: AccountRepository,
    followerRepository: FollowerRepository,
    deliveryQueueRepository: DeliveryQueueRepository,
    val domain: String,
    val actorDirectory: ActorDirectory,
    notePublisher: NotePublisher,
    notePoster: NotePoster,
    actorPublisher: ActorPublisher,
    val noteStore: NoteStore,
    val feedService: FeedService,
) {
    val adminLoginService: AdminLoginService = AdminLoginService(passwordHash)

    val accountService: AccountService = AccountService(
        accounts = accountRepository,
        followers = followerRepository,
        deliveryQueue = deliveryQueueRepository,
        actorPublisher = actorPublisher,
        domain = domain,
    )

    val noteService: NoteService = NoteService(
        directory = actorDirectory,
        publisher = notePublisher,
        poster = notePoster,
        notes = noteStore,
    )

    val deliveryQueueService: DeliveryQueueService = DeliveryQueueService(deliveryQueueRepository)
}
