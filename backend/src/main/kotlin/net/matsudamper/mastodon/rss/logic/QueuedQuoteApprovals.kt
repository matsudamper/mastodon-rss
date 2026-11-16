package net.matsudamper.mastodon.rss.logic

import java.time.Instant
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.ActorPrivateKey
import net.matsudamper.kotpub.actor.LocalActor
import net.matsudamper.kotpub.actor.RemoteActor
import net.matsudamper.kotpub.quote.QuoteApprovals
import net.matsudamper.mastodon.rss.repository.DeliveryQueueRepository
import net.matsudamper.mastodon.rss.repository.QuoteAcceptPost

/**
 * 引用の承認を配信キューに繋ぐ。
 *
 * 相手ごとのブロックは無いので、認めないのは受信を止めているドメインの相手だけ
 */
class QueuedQuoteApprovals(
    private val directory: ActorDirectory,
    private val actorKey: ActorPrivateKey,
    private val domainBlocks: DomainBlockService,
    private val deliveryQueue: DeliveryQueueRepository,
    private val clock: () -> Instant,
) : QuoteApprovals {
    override fun findAuthor(username: String): LocalActor? =
        directory.resolve(username)?.let { LocalActor(urls = it, privateKey = actorKey) }

    override fun refuses(
        username: String,
        quoterActorId: String,
    ): Boolean = domainBlocks.blocksInboxFrom(quoterActorId)

    override fun enqueueAccept(
        username: String,
        quoter: RemoteActor,
        acceptBody: String,
    ) {
        deliveryQueue.enqueueQuoteAccept(
            QuoteAcceptPost(
                username = username,
                inbox = quoter.inbox,
                body = acceptBody,
                enqueuedAt = clock(),
            ),
        )
    }
}
