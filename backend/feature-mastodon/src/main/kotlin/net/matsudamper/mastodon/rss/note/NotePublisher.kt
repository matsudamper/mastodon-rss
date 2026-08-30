package net.matsudamper.mastodon.rss.note

import java.time.Instant
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.crypto.UuidV7
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
        val publishedAt = Instant.now()
        val publicId = UuidV7.generate(publishedAt.toEpochMilli())
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

        val targets = followers.deliveryTargets(sender.username)
        var delivered = 0

        targets.forEach { inbox ->
            when (val result = delivery.deliver(inbox = inbox, sender = sender, body = body)) {
                is DeliveryResult.Delivered -> {
                    delivered++
                }

                is DeliveryResult.Failed -> {
                    // 再送しないので、届かなかったことはここに残っているものが唯一の手がかり
                    logger.warn("投稿を配れなかった: ${sender.acct} → $inbox ${result.reason}")
                }
            }
        }

        logger.info("投稿を配った: ${sender.acct} $publicId 宛先=${targets.size} 成功=$delivered")

        return PublishedNote(
            publicId = publicId,
            url = urls.noteId,
            contentHtml = contentHtml,
            publishedAt = publishedAt,
            targets = targets.size,
            delivered = delivered,
        )
    }

    private fun createActivity(
        sender: ActorUrls,
        urls: NoteUrls,
        contentHtml: String,
        publishedAt: Instant,
    ): CreateNote {
        val published = publishedAt.toActivityPubPublished()

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
                atomUri = urls.noteId,
            ),
        )
    }
}

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
