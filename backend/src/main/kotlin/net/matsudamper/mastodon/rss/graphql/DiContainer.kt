package net.matsudamper.mastodon.rss.graphql

import net.matsudamper.mastodon.rss.actor.ActorDirectory
import net.matsudamper.mastodon.rss.actor.ActorPublisher
import net.matsudamper.mastodon.rss.crypto.PasswordHash
import net.matsudamper.mastodon.rss.logic.AccountIconFiles
import net.matsudamper.mastodon.rss.logic.AccountService
import net.matsudamper.mastodon.rss.logic.ActorEnqueuer
import net.matsudamper.mastodon.rss.logic.AdminLoginService
import net.matsudamper.mastodon.rss.logic.DeliveryQueueService
import net.matsudamper.mastodon.rss.logic.FeedService
import net.matsudamper.mastodon.rss.logic.NoteEnqueuer
import net.matsudamper.mastodon.rss.logic.NoteReader
import net.matsudamper.mastodon.rss.note.NoteStore
import net.matsudamper.mastodon.rss.remoteactor.RemoteActorIconUrls
import net.matsudamper.mastodon.rss.repository.AccountRepository
import net.matsudamper.mastodon.rss.repository.DeliveryQueueRepository
import net.matsudamper.mastodon.rss.repository.FeedHeaderRepository
import net.matsudamper.mastodon.rss.repository.FollowerRepository
import net.matsudamper.mastodon.rss.repository.NoteReactionRepository

class DiContainer(
    passwordHash: PasswordHash?,
    accountRepository: AccountRepository,
    followerRepository: FollowerRepository,
    deliveryQueueRepository: DeliveryQueueRepository,
    val domain: String,
    val actorDirectory: ActorDirectory,
    val feedHeaderRepository: FeedHeaderRepository,
    val noteEnqueuer: NoteEnqueuer,
    actorPublisher: ActorPublisher,
    actorEnqueuer: ActorEnqueuer,
    accountIconFiles: AccountIconFiles,
    val noteStore: NoteStore,
    val noteReactionRepository: NoteReactionRepository,
    val feedService: FeedService,
) {
    val adminLoginService: AdminLoginService = AdminLoginService(passwordHash)

    val accountService: AccountService = AccountService(
        accounts = accountRepository,
        followers = followerRepository,
        actorPublisher = actorPublisher,
        actorEnqueuer = actorEnqueuer,
        iconFiles = accountIconFiles,
        domain = domain,
    )

    /**
     * フォロワーのアイコンは配信元ではなくこちらを指す URL で返す
     */
    val remoteActorIconUrls: RemoteActorIconUrls = RemoteActorIconUrls(domain)

    val noteReader: NoteReader = NoteReader(
        directory = actorDirectory,
        notes = noteStore,
    )

    val deliveryQueueService: DeliveryQueueService = DeliveryQueueService(deliveryQueueRepository)
}
