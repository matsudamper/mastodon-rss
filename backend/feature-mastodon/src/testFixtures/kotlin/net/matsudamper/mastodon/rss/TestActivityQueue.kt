package net.matsudamper.mastodon.rss

import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.delivery.ActivityQueue
import net.matsudamper.mastodon.rss.delivery.QueuedActivityKind
import net.matsudamper.mastodon.rss.entity.PublicNoteId

/**
 * 投函の差し替え。投函されたものを宛先ごとに溜めておく。
 *
 * 実際に送るのは配信ワーカーなので、送る中身と宛先が決まっていることだけを確かめる。
 *
 * @param enqueueFails 投函そのものに失敗させる。DB が書けない状態を作る
 */
class TestActivityQueue(
    private val enqueueFails: Boolean = false,
) : ActivityQueue {
    val queued: MutableList<Queued> = mutableListOf()

    override fun enqueue(
        kind: QueuedActivityKind,
        sender: ActorUrls,
        inboxes: List<String>,
        body: String,
        notePublicId: PublicNoteId?,
    ): Int {
        if (enqueueFails) error("投函できない")

        inboxes.forEach { inbox ->
            queued += Queued(kind = kind, inbox = inbox, sender = sender, body = body, notePublicId = notePublicId)
        }
        return inboxes.size
    }

    data class Queued(
        val kind: QueuedActivityKind,
        val inbox: String,
        val sender: ActorUrls,
        val body: String,
        val notePublicId: PublicNoteId?,
    )
}
