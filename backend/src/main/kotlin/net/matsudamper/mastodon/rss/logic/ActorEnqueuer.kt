package net.matsudamper.mastodon.rss.logic

import java.time.Instant
import net.matsudamper.mastodon.rss.actor.ActorPublisher
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.repository.ActorUpdatePost
import net.matsudamper.mastodon.rss.repository.DeliveryQueueRepository
import net.matsudamper.mastodon.rss.repository.FollowerRepository
import org.slf4j.LoggerFactory

/**
 * アクターの変化を組み立てて、配信を投函する。
 *
 * `:backend:feature-mastodon` の [ActorPublisher] が送るものを組み立て、
 * `:backend:repository` の投函の口が書く。両方を知っているのは `:backend` だけなので、
 * 繋ぐのはここになる。投稿に対する [NoteEnqueuer] と同じ役回り。
 */
class ActorEnqueuer(
    private val publisher: ActorPublisher,
    private val followers: FollowerRepository,
    private val deliveryQueue: DeliveryQueueRepository,
) {
    private val logger = LoggerFactory.getLogger(ActorEnqueuer::class.java)

    /**
     * 今のアクター情報をフォロワーに配るために投函する。
     *
     * 保存が済んでから呼ぶこと。組み立てるのは保存されている中身なので、
     * 先に呼ぶと 1 つ前の内容が配られる。
     */
    fun enqueueUpdate(sender: ActorUrls) {
        val inboxes = followers.deliveryTargets(sender.username)

        val deliveries = deliveryQueue.enqueueActorUpdate(
            ActorUpdatePost(
                username = sender.username,
                body = publisher.prepareUpdate(sender),
                inboxes = inboxes,
                enqueuedAt = Instant.now(),
            ),
        )

        logger.info("アクターの更新を投函した: ${sender.acct} 宛先=$deliveries")
    }
}
