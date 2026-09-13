package net.matsudamper.mastodon.rss.graphql

import net.matsudamper.mastodon.rss.actor.ActorDirectory
import net.matsudamper.mastodon.rss.actor.ActorPublisher
import net.matsudamper.mastodon.rss.crypto.PasswordHash
import net.matsudamper.mastodon.rss.logic.AccountIconFiles
import net.matsudamper.mastodon.rss.logic.AccountService
import net.matsudamper.mastodon.rss.logic.AdminLoginService
import net.matsudamper.mastodon.rss.logic.DeliveryQueueService
import net.matsudamper.mastodon.rss.logic.FeedService
import net.matsudamper.mastodon.rss.logic.NoteEnqueuer
import net.matsudamper.mastodon.rss.logic.NoteService
import net.matsudamper.mastodon.rss.note.NotePublisher
import net.matsudamper.mastodon.rss.note.NoteStore
import net.matsudamper.mastodon.rss.repository.AccountRepository
import net.matsudamper.mastodon.rss.repository.DeliveryQueueRepository
import net.matsudamper.mastodon.rss.repository.FeedHeaderRepository
import net.matsudamper.mastodon.rss.repository.FollowerRepository

class DiContainer(
    passwordHash: PasswordHash?,
    accountRepository: AccountRepository,
    followerRepository: FollowerRepository,
    deliveryQueueRepository: DeliveryQueueRepository,
    val domain: String,
    val actorDirectory: ActorDirectory,
    val feedHeaderRepository: FeedHeaderRepository,
    notePublisher: NotePublisher,
    noteEnqueuer: NoteEnqueuer,
    actorPublisher: ActorPublisher,
    accountIconFiles: AccountIconFiles,
    val noteStore: NoteStore,
    val feedService: FeedService,
) {
    val adminLoginService: AdminLoginService = AdminLoginService(passwordHash)

    val accountService: AccountService = AccountService(
        accounts = accountRepository,
        followers = followerRepository,
        deliveryQueue = deliveryQueueRepository,
        actorPublisher = actorPublisher,
        iconFiles = accountIconFiles,
        domain = domain,
    )

    val noteService: NoteService = NoteService(
        directory = actorDirectory,
        publisher = notePublisher,
        poster = noteEnqueuer,
        notes = noteStore,
    )

    val deliveryQueueService: DeliveryQueueService = DeliveryQueueService(deliveryQueueRepository)
}
