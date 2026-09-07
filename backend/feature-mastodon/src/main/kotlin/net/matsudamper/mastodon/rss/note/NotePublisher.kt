package net.matsudamper.mastodon.rss.note

import java.time.Instant
import net.matsudamper.mastodon.rss.activity.ActivityStreamsIri
import net.matsudamper.mastodon.rss.activity.CreateNoteActivity
import net.matsudamper.mastodon.rss.activity.DeleteNoteActivity
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.crypto.UuidV7
import net.matsudamper.mastodon.rss.delivery.ActivityDelivery
import net.matsudamper.mastodon.rss.delivery.DeliveryResult
import net.matsudamper.mastodon.rss.entity.PublicNoteId
import net.matsudamper.mastodon.rss.follower.FollowerStore
import net.matsudamper.mastodon.rss.json.AppJson
import org.slf4j.LoggerFactory

/**
 * 投稿の `Create{Note}` を組み立てる（[prepare]）。投稿の削除はその場で配る（[delete]）。
 *
 * [prepare] は DB も触らず HTTP も出さない。記録と投函は `:backend` が repository の
 * 投函の口で 1 トランザクションにまとめる。記録と配信をここで続けて行うと、
 * 記事の投稿済み化と別々に確定して、途中で落ちたときに同じ記事を二重に投稿する。
 *
 * [delete] はまだキューに載せていない。載せているのは投稿だけで、削除は
 * 管理画面からの操作でしか起きないため。失敗しても再送はしないのでログに残るだけ。
 */
class NotePublisher(
    private val notes: NoteStore,
    private val followers: FollowerStore,
    private val delivery: ActivityDelivery,
) {
    private val logger = LoggerFactory.getLogger(NotePublisher::class.java)

    /**
     * 投稿の id と時刻を決めて、フォロワーに送る `Create{Note}` の JSON を組み立てる。
     *
     * @param contentHtml 本文。サニタイズ済みの HTML を渡すこと。ここでは中身を検査しない
     */
    fun prepare(
        sender: ActorUrls,
        contentHtml: String,
    ): PreparedNote {
        val publishedAt = Instant.now()
        val publicId = PublicNoteId(UuidV7.generate(publishedAt.toEpochMilli()))
        val urls = NoteUrls(domain = sender.domain, publicId = publicId)

        val activityJson = AppJson.encodeToString(
            CreateNoteActivity.serializer(),
            createActivity(sender = sender, urls = urls, contentHtml = contentHtml, publishedAt = publishedAt),
        )

        return PreparedNote(
            publicId = publicId,
            url = urls.noteUrl,
            contentHtml = contentHtml,
            publishedAt = publishedAt,
            activityJson = activityJson,
        )
    }

    /**
     * 投稿を消して、消したことをフォロワーに配る。
     *
     * 記録を先に消す。配信が先だと、`Delete` を受け取った相手が確かめに来たときに
     * まだ本文を返してしまう。
     *
     * 配れなかった相手のタイムラインには投稿が残る。削除はキューに載せていないので送り直さない。
     *
     * @return 記録が無ければ null
     */
    suspend fun delete(
        sender: ActorUrls,
        publicId: PublicNoteId,
    ): DeletedNote? {
        // 他のアカウントの投稿を publicId だけで消せないようにする
        notes.find(publicId)?.takeIf { it.username.equals(sender.username, ignoreCase = true) }
            ?: return null

        val urls = NoteUrls(domain = sender.domain, publicId = publicId)
        notes.delete(publicId)

        val activityBodyBytes = AppJson.encodeToString(
            DeleteNoteActivity.serializer(),
            deleteActivity(sender = sender, urls = urls),
        ).toByteArray()

        val result = deliverToFollowers(sender = sender, body = activityBodyBytes)

        logger.info(
            "投稿の削除を配った: ${sender.acct} $publicId 宛先=${result.deliveryAttemptCount} 成功=${result.delivered}",
        )

        return DeletedNote(publicId = publicId)
    }

    private suspend fun deliverToFollowers(
        sender: ActorUrls,
        body: ByteArray,
    ): DeliveryCount {
        val deliveryInboxes = followers.deliveryTargets(sender.username)
        var delivered = 0

        deliveryInboxes.forEach { inbox ->
            when (val result = delivery.deliver(inbox = inbox, sender = sender, body = body)) {
                is DeliveryResult.Delivered -> {
                    delivered++
                }

                is DeliveryResult.Failed -> {
                    // 再送しないので、届かなかったことはここに残っているものが唯一の手がかり
                    logger.warn("配れなかった: ${sender.acct} → $inbox ${result.reason}")
                }
            }
        }

        return DeliveryCount(deliveryAttemptCount = deliveryInboxes.size, delivered = delivered)
    }

    private data class DeliveryCount(
        val deliveryAttemptCount: Int,
        val delivered: Int,
    )

    private fun deleteActivity(
        sender: ActorUrls,
        urls: NoteUrls,
    ): DeleteNoteActivity = DeleteNoteActivity(
        id = urls.deleteId,
        actor = sender.actorId,
        to = listOf(ActivityStreamsIri.PUBLIC_AUDIENCE),
        cc = listOf(sender.followers),
        target = DeleteNoteActivity.Tombstone(id = urls.noteId),
    )

    private fun createActivity(
        sender: ActorUrls,
        urls: NoteUrls,
        contentHtml: String,
        publishedAt: Instant,
    ): CreateNoteActivity {
        val published = publishedAt.toActivityPubPublished()

        return CreateNoteActivity(
            id = urls.createId,
            actor = sender.actorId,
            published = published,
            to = listOf(ActivityStreamsIri.PUBLIC_AUDIENCE),
            cc = listOf(sender.followers),
            target = Note(
                id = urls.noteId,
                attributedTo = sender.actorId,
                content = contentHtml,
                published = published,
                to = listOf(ActivityStreamsIri.PUBLIC_AUDIENCE),
                cc = listOf(sender.followers),
                url = urls.noteUrl,
                atomUri = urls.noteUrl,
            ),
        )
    }
}

data class DeletedNote(
    val publicId: PublicNoteId,
)

/**
 * 組み立てた投稿。記録する中身と、宛先に送る `Create{Note}` を持つ。
 *
 * @param url 相手がパーマリンクとして開く URL
 * @param activityJson 署名対象になる JSON。宛先が何件でも同じものを送る
 */
data class PreparedNote(
    val publicId: PublicNoteId,
    val url: String,
    val contentHtml: String,
    val publishedAt: Instant,
    val activityJson: String,
)
