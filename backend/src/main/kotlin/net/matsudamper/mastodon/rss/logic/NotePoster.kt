package net.matsudamper.mastodon.rss.logic

import java.time.Instant
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.entity.PublicNoteId as MastodonPublicNoteId
import net.matsudamper.mastodon.rss.note.NotePublisher
import net.matsudamper.mastodon.rss.note.PreparedNote
import net.matsudamper.mastodon.rss.repository.DeliveryQueueRepository
import net.matsudamper.mastodon.rss.repository.EnqueueNoteResult
import net.matsudamper.mastodon.rss.repository.FollowerRepository
import net.matsudamper.mastodon.rss.repository.NewNote
import net.matsudamper.mastodon.rss.repository.NotePost
import net.matsudamper.mastodon.rss.repository.RecordedNotePost
import net.matsudamper.mastodon.rss.repository.entity.FeedItemId
import net.matsudamper.mastodon.rss.shared.PublicNoteId
import org.slf4j.LoggerFactory

/**
 * 投稿を組み立てて、記録と配信の投函を 1 回で確定させる。
 *
 * `:backend:feature-mastodon` の [NotePublisher] が `Create{Note}` を組み立て、
 * `:backend:repository` の投函の口が記録・投函・記事の投稿済み化を 1 トランザクションで書く。
 * 両方を知っているのは `:backend` だけなので、繋ぐのはここになる。
 *
 * 配信はここでは行わない。投函した行は配信ワーカーが拾って送る。
 */
class NotePoster(
    private val publisher: NotePublisher,
    private val followers: FollowerRepository,
    private val deliveryQueue: DeliveryQueueRepository,
) {
    private val logger = LoggerFactory.getLogger(NotePoster::class.java)

    /**
     * @param contentHtml 本文。サニタイズ済みの HTML を渡すこと
     * @param feedItemId 記事から投稿するなら、その記事。管理画面からの告知は null
     * @return 記事が既に投稿済みか消えていて何も書かなかったなら null
     */
    fun post(
        sender: ActorUrls,
        contentHtml: String,
        feedItemId: FeedItemId?,
    ): QueuedNote? {
        val prepared = publisher.prepare(sender = sender, contentHtml = contentHtml)
        val inboxes = followers.deliveryTargets(sender.username)

        val result = deliveryQueue.enqueueNote(
            NotePost(
                note = prepared.toNewNote(sender),
                body = prepared.activityJson,
                inboxes = inboxes,
                enqueuedAt = prepared.publishedAt,
                feedItemId = feedItemId,
            ),
        )

        return when (result) {
            is EnqueueNoteResult.Queued -> {
                logger.info("投稿を投函した: ${sender.acct} ${prepared.publicId} 宛先=${result.deliveries}")
                prepared.toQueuedNote()
            }

            EnqueueNoteResult.FeedItemNotPending -> null
        }
    }

    /**
     * 記録済みの投稿を投函し直す。
     *
     * 記事に投稿が紐付いているのに未投稿のまま残っている行に使う。新しく作ると、
     * 既に届いている記事が別の投稿としてもう一度並ぶ。
     *
     * @return 投稿の記録が無い、または記事が既に投稿済みで何も書かなかったなら null
     */
    fun repost(
        sender: ActorUrls,
        publicId: PublicNoteId,
        feedItemId: FeedItemId,
    ): QueuedNote? {
        val prepared = publisher.prepareRecorded(sender = sender, publicId = MastodonPublicNoteId(publicId.value))
            ?: return null
        val inboxes = followers.deliveryTargets(sender.username)

        val result = deliveryQueue.requeueNote(
            RecordedNotePost(
                publicId = publicId,
                username = sender.username,
                body = prepared.activityJson,
                inboxes = inboxes,
                enqueuedAt = Instant.now(),
                feedItemId = feedItemId,
            ),
        )

        return when (result) {
            is EnqueueNoteResult.Queued -> {
                logger.info("記録済みの投稿を投函し直した: ${sender.acct} ${prepared.publicId} 宛先=${result.deliveries}")
                prepared.toQueuedNote()
            }

            EnqueueNoteResult.FeedItemNotPending -> null
        }
    }

    private fun PreparedNote.toNewNote(sender: ActorUrls): NewNote = NewNote(
        username = sender.username,
        publicId = PublicNoteId(publicId.value),
        contentHtml = contentHtml,
        publishedAt = publishedAt,
    )

    private fun PreparedNote.toQueuedNote(): QueuedNote = QueuedNote(
        publicId = publicId,
        url = url,
        contentHtml = contentHtml,
        publishedAt = publishedAt,
    )
}

/**
 * 記録して投函した投稿。相手に届くのはこの後
 */
data class QueuedNote(
    val publicId: MastodonPublicNoteId,
    val url: String,
    val contentHtml: String,
    val publishedAt: Instant,
)
