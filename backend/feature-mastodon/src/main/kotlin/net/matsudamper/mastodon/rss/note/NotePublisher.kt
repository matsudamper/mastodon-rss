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
import net.matsudamper.mastodon.rss.url.WebPageUrls
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
    private val webPages: WebPageUrls?,
) {
    private val logger = LoggerFactory.getLogger(NotePublisher::class.java)

    /**
     * 投稿を組み立てる。まだ記録も配信もしない。
     *
     * @param contentHtml 本文。サニタイズ済みの HTML を渡すこと。ここでは中身を検査しない
     */
    fun create(
        sender: ActorUrls,
        contentHtml: String,
    ): StoredNote {
        val publishedAt = Instant.now()
        return StoredNote(
            publicId = PublicNoteId(UuidV7.generate(publishedAt.toEpochMilli())),
            username = sender.username,
            contentHtml = contentHtml,
            publishedAt = publishedAt,
        )
    }

    /**
     * 投稿がまだ記録されていなければ記録する。
     *
     * フィード記事では repository 側が Note と記事の紐付けを同じトランザクションで
     * 保存する。その後の配信口からも同じ [NoteStore] を見える状態に揃えるために使う。
     */
    fun recordIfMissing(note: StoredNote) {
        val existing = notes.find(note.publicId)
        if (existing == null) {
            notes.add(note)
        } else {
            check(existing == note) { "同じ id の別投稿が既に記録されている" }
        }
    }

    /**
     * 記録済みの投稿をフォロワーへ配る。
     *
     * 同じ [publicId] で呼び直すと同じ `Create` / `Note` の id と本文・公開日時を使う。
     */
    suspend fun deliver(
        sender: ActorUrls,
        publicId: PublicNoteId,
    ): DeliverResult {
        val note = notes.find(publicId)?.takeIf { it.username.equals(sender.username, ignoreCase = true) }
            ?: return DeliverResult.NotFound
        val urls = NoteUrls(domain = sender.domain, publicId = note.publicId)

        val activityBodyBytes = AppJson.encodeToString(
            CreateNoteActivity.serializer(),
            CreateNoteActivityFactory.create(sender = sender, note = note, webPages = webPages),
        ).toByteArray()

        val result = deliverToFollowers(sender = sender, body = activityBodyBytes)

        logger.info(
            "投稿を配った: ${sender.acct} ${note.publicId} 宛先=${result.deliveryAttemptCount} 成功=${result.delivered}",
        )

        return DeliverResult.Success(
            PublishedNote(
                publicId = note.publicId,
                url = urls.noteUrl,
                contentHtml = note.contentHtml,
                publishedAt = note.publishedAt,
                deliveryAttemptCount = result.deliveryAttemptCount,
                delivered = result.delivered,
            ),
        )
    }

    /**
     * 投稿を記録して、そのまま全フォロワーに配る。
     *
     * @param contentHtml 本文。サニタイズ済みの HTML を渡すこと。ここでは中身を検査しない
     */
    suspend fun publish(
        sender: ActorUrls,
        contentHtml: String,
    ): PublishedNote {
        val note = create(sender = sender, contentHtml = contentHtml)
        recordIfMissing(note)
        return when (val result = deliver(sender = sender, publicId = note.publicId)) {
            is DeliverResult.Success -> result.published
            DeliverResult.NotFound -> error("作成した投稿が見つからない")
        }
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

    sealed interface DeliverResult {
        data class Success(
            val published: PublishedNote,
        ) : DeliverResult

        data object NotFound : DeliverResult
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
}

data class DeletedNote(
    val publicId: PublicNoteId,
)

/** 配信した結果。 */
data class PublishedNote(
    val publicId: PublicNoteId,
    val url: String,
    val contentHtml: String,
    val publishedAt: Instant,
    val deliveryAttemptCount: Int,
    val delivered: Int,
)
