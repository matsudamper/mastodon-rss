package net.matsudamper.mastodon.rss.note

import java.time.Instant
import java.util.UUID
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.delivery.ActivityDelivery
import net.matsudamper.mastodon.rss.delivery.DeliveryResult
import net.matsudamper.mastodon.rss.follower.FollowerStore
import net.matsudamper.mastodon.rss.json.AppJson
import org.slf4j.LoggerFactory

/**
 * 投稿を作って全フォロワーに配る。
 *
 * 記録してから配る。相手は受け取った直後にパーマリンクを引きに来ることがあるので、
 * 配信が先だと 404 を返してしまう。
 *
 * 配信はその場で 1 件ずつ送る。失敗しても再送はしないのでログに残すだけ。
 * 溜めて送り直す仕組みが要るのは、実際に取りこぼしが見えてからでよい。
 */
class NotePublisher(
    private val notes: NoteStore,
    private val followers: FollowerStore,
    private val delivery: ActivityDelivery,
) {
    private val logger = LoggerFactory.getLogger(NotePublisher::class.java)

    /**
     * @param contentHtml 本文。サニタイズ済みの HTML を渡すこと。ここでは中身を検査しない
     */
    suspend fun publish(
        sender: ActorUrls,
        contentHtml: String,
    ): PublishedNote {
        val publicId = UUID.randomUUID().toString()
        val publishedAt = Instant.now()
        val urls = NoteUrls(domain = sender.domain, publicId = publicId)

        notes.add(
            StoredNote(
                publicId = publicId,
                username = sender.username,
                contentHtml = contentHtml,
                publishedAt = publishedAt,
            ),
        )

        val body = AppJson.encodeToString(
            CreateNote.serializer(),
            createActivity(sender = sender, urls = urls, contentHtml = contentHtml, publishedAt = publishedAt),
        ).toByteArray()

        val result = deliverToFollowers(sender = sender, body = body)

        logger.info("投稿を配った: ${sender.acct} $publicId 宛先=${result.targets} 成功=${result.delivered}")

        return PublishedNote(
            publicId = publicId,
            url = urls.noteId,
            contentHtml = contentHtml,
            publishedAt = publishedAt,
            targets = result.targets,
            delivered = result.delivered,
        )
    }

    /**
     * 投稿を消して、消したことをフォロワーに配る。
     *
     * 記録を先に消す。配信が先だと、`Delete` を受け取った相手が確かめに来たときに
     * まだ本文を返してしまう。
     *
     * 配れなかった相手のタイムラインには投稿が残る。再送しないのは [publish] と同じ。
     *
     * @return 記録が無ければ null
     */
    suspend fun delete(
        sender: ActorUrls,
        publicId: String,
    ): DeletedNote? {
        // 他のアカウントの投稿を publicId だけで消せないようにする
        notes.find(publicId)?.takeIf { it.username.equals(sender.username, ignoreCase = true) }
            ?: return null

        val urls = NoteUrls(domain = sender.domain, publicId = publicId)
        notes.delete(publicId)

        val body = AppJson.encodeToString(
            DeleteNote.serializer(),
            deleteActivity(sender = sender, urls = urls),
        ).toByteArray()

        val result = deliverToFollowers(sender = sender, body = body)

        logger.info("投稿の削除を配った: ${sender.acct} $publicId 宛先=${result.targets} 成功=${result.delivered}")

        return DeletedNote(
            publicId = publicId,
            targets = result.targets,
            delivered = result.delivered,
        )
    }

    private suspend fun deliverToFollowers(
        sender: ActorUrls,
        body: ByteArray,
    ): DeliveryCount {
        val targets = followers.deliveryTargets(sender.username)
        var delivered = 0

        targets.forEach { inbox ->
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

        return DeliveryCount(targets = targets.size, delivered = delivered)
    }

    private data class DeliveryCount(
        val targets: Int,
        val delivered: Int,
    )

    private fun deleteActivity(
        sender: ActorUrls,
        urls: NoteUrls,
    ): DeleteNote = DeleteNote(
        id = urls.deleteId,
        actor = sender.actorId,
        to = listOf(PUBLIC_AUDIENCE),
        target = Tombstone(id = urls.noteId),
    )

    private fun createActivity(
        sender: ActorUrls,
        urls: NoteUrls,
        contentHtml: String,
        publishedAt: Instant,
    ): CreateNote {
        val published = publishedAt.toString()

        return CreateNote(
            id = urls.createId,
            actor = sender.actorId,
            published = published,
            to = listOf(PUBLIC_AUDIENCE),
            cc = listOf(sender.followers),
            target = Note(
                id = urls.noteId,
                attributedTo = sender.actorId,
                content = contentHtml,
                published = published,
                to = listOf(PUBLIC_AUDIENCE),
                cc = listOf(sender.followers),
                url = urls.noteId,
            ),
        )
    }
}

/**
 * 消した結果。
 *
 * @param targets `Delete` を送った宛先の数
 * @param delivered そのうち相手が受け取ったもの。届かなかった相手には投稿が残る
 */
data class DeletedNote(
    val publicId: String,
    val targets: Int,
    val delivered: Int,
)

/**
 * 配信した結果。
 *
 * @param targets 送った宛先の数。`sharedInbox` でまとまるのでフォロワーの数とは一致しない
 * @param delivered そのうち相手が受け取ったもの
 */
data class PublishedNote(
    val publicId: String,
    val url: String,
    val contentHtml: String,
    val publishedAt: Instant,
    val targets: Int,
    val delivered: Int,
)
