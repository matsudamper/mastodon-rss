package net.matsudamper.mastodon.rss.note

import java.time.Instant
import net.matsudamper.mastodon.rss.activity.ActivityStreamsIri
import net.matsudamper.mastodon.rss.activity.CreateNoteActivity
import net.matsudamper.mastodon.rss.activity.DeleteNoteActivity
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.crypto.UuidV7
import net.matsudamper.mastodon.rss.delivery.ActivityQueue
import net.matsudamper.mastodon.rss.delivery.QueuedActivityKind
import net.matsudamper.mastodon.rss.entity.PublicNoteId
import net.matsudamper.mastodon.rss.follower.FollowerStore
import net.matsudamper.mastodon.rss.json.AppJson
import net.matsudamper.mastodon.rss.url.WebPageUrls
import org.slf4j.LoggerFactory

/**
 * 投稿の `Create{Note}` を組み立てる（[prepare] / [prepareRecorded]）。投稿の削除は配信キューに投函する（[delete]）。
 *
 * [prepare] は DB も触らず HTTP も出さない。記録と投函は `:backend` が repository の
 * 投函の口で 1 トランザクションにまとめる。記録と配信をここで続けて行うと、
 * 記事の投稿済み化と別々に確定して、途中で落ちたときに同じ記事を二重に投稿する。
 *
 * [delete] は組み立てと投函の両方を行う。投稿の記録を消すのと同じ順序で決まるので、
 * `Create` のように呼び出し側に組み立てだけを返すと、順序を外から間違えられる。
 */
class NotePublisher(
    private val notes: NoteStore,
    private val followers: FollowerStore,
    private val queue: ActivityQueue,
    private val webPages: WebPageUrls?,
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
        val note = StoredNote(
            publicId = PublicNoteId(UuidV7.generate(publishedAt.toEpochMilli())),
            username = sender.username,
            contentHtml = contentHtml,
            publishedAt = publishedAt,
        )

        return note.toPrepared(sender)
    }

    /**
     * 記録済みの投稿から、同じ id・本文・公開日時で `Create{Note}` を組み立て直す。
     *
     * 配り切れていない投稿を投函し直すのに使う。新しく作ると同じ記事が別の投稿として届く。
     * 同じ id で送る限り、受け取った側はアクティビティの id で冪等に扱う。
     *
     * @return 記録が無いか、別のアカウントの投稿なら null
     */
    fun prepareRecorded(
        sender: ActorUrls,
        publicId: PublicNoteId,
    ): PreparedNote? {
        val note = notes.find(publicId)?.takeIf { it.username.equals(sender.username, ignoreCase = true) }
            ?: return null

        return note.toPrepared(sender)
    }

    /**
     * 組み立ては [CreateNoteActivityFactory] に任せる。フォロー成立後の再配信と同じものを
     * 作らないと、同じ投稿が相手のタイムラインに 2 度並ぶ
     */
    private fun StoredNote.toPrepared(sender: ActorUrls): PreparedNote {
        val urls = NoteUrls(domain = sender.domain, publicId = publicId)

        return PreparedNote(
            publicId = publicId,
            url = urls.noteUrl,
            contentHtml = contentHtml,
            publishedAt = publishedAt,
            activityJson = AppJson.encodeToString(
                CreateNoteActivity.serializer(),
                CreateNoteActivityFactory.create(sender = sender, note = this, webPages = webPages),
            ),
        )
    }

    /**
     * 投稿を消して、消したことをフォロワーに配る。
     *
     * 記録を先に消す。投函が先だと、`Delete` を受け取った相手が確かめに来たときに
     * まだ本文を返してしまう。記録を消すと、まだ配っていない `Create` も一緒に消える。
     *
     * @return 記録が無ければ null
     */
    fun delete(
        sender: ActorUrls,
        publicId: PublicNoteId,
    ): DeletedNote? {
        // 他のアカウントの投稿を publicId だけで消せないようにする
        notes.find(publicId)?.takeIf { it.username.equals(sender.username, ignoreCase = true) }
            ?: return null

        val urls = NoteUrls(domain = sender.domain, publicId = publicId)
        notes.delete(publicId)

        val body = AppJson.encodeToString(
            DeleteNoteActivity.serializer(),
            deleteActivity(sender = sender, urls = urls),
        )

        // 投稿は既に消えているので、行は投稿に繋がない。繋ぐと外部キーで入らない
        val queued = queue.enqueue(
            kind = QueuedActivityKind.DELETE_NOTE,
            sender = sender,
            inboxes = followers.deliveryTargets(sender.username),
            body = body,
            notePublicId = null,
        )

        logger.info("投稿の削除を投函した: ${sender.acct} $publicId 宛先=$queued")

        return DeletedNote(publicId = publicId)
    }

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
