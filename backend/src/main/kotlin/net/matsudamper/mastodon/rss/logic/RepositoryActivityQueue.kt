package net.matsudamper.mastodon.rss.logic

import java.time.Instant
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.delivery.ActivityQueue
import net.matsudamper.mastodon.rss.delivery.QueuedActivityKind
import net.matsudamper.mastodon.rss.entity.PublicNoteId as MastodonPublicNoteId
import net.matsudamper.mastodon.rss.repository.ActivityPost
import net.matsudamper.mastodon.rss.repository.DeliveryKind
import net.matsudamper.mastodon.rss.repository.DeliveryQueueRepository
import net.matsudamper.mastodon.rss.shared.PublicNoteId

/**
 * `:backend:feature-mastodon` の投函の口を配信キューに繋ぐ。
 *
 * 投稿の `Create` は記録と投稿済み化を同じトランザクションに入れる必要があるので
 * [NoteEnqueuer] が別の口を通る。こちらは既に中身が決まっているものだけを扱う。
 */
class RepositoryActivityQueue(
    private val deliveryQueue: DeliveryQueueRepository,
    private val clock: () -> Instant,
) : ActivityQueue {
    override fun enqueue(
        kind: QueuedActivityKind,
        sender: ActorUrls,
        inboxes: List<String>,
        body: String,
        notePublicId: MastodonPublicNoteId?,
    ): Int {
        if (inboxes.isEmpty()) return 0

        return deliveryQueue.enqueueActivity(
            ActivityPost(
                kind = kind.toDeliveryKind(),
                username = sender.username,
                body = body,
                inboxes = inboxes,
                enqueuedAt = clock(),
                notePublicId = notePublicId?.let { PublicNoteId(it.value) },
            ),
        )
    }

    private fun QueuedActivityKind.toDeliveryKind(): DeliveryKind =
        when (this) {
            QueuedActivityKind.CREATE_NOTE -> DeliveryKind.CREATE_NOTE
            QueuedActivityKind.ACCEPT_FOLLOW -> DeliveryKind.ACCEPT_FOLLOW
            QueuedActivityKind.DELETE_NOTE -> DeliveryKind.DELETE_NOTE
            QueuedActivityKind.DELETE_ACTOR -> DeliveryKind.DELETE_ACTOR
            QueuedActivityKind.UPDATE_ACTOR -> DeliveryKind.UPDATE_ACTOR
        }
}
