package net.matsudamper.mastodon.rss.actor

import net.matsudamper.mastodon.rss.activity.ActivityStreamsIri
import net.matsudamper.mastodon.rss.activity.DeleteActorActivity
import net.matsudamper.mastodon.rss.crypto.UuidV7
import net.matsudamper.mastodon.rss.delivery.ActivityDelivery
import net.matsudamper.mastodon.rss.delivery.DeliveryResult
import net.matsudamper.mastodon.rss.entity.ActivityPubId
import net.matsudamper.mastodon.rss.follower.FollowerStore
import net.matsudamper.mastodon.rss.json.AppJson
import net.matsudamper.mastodon.rss.note.NoteStore
import org.slf4j.LoggerFactory

/**
 * アクターを消して、消したことを配る。
 *
 * 配る前に配信先を控えてから、フォロワーと投稿の記録を消す。記録を残したまま配ると、
 * `Delete` を受けた相手が確かめに来たときにまだアクターの中身を返してしまう。
 *
 * 配信は [net.matsudamper.mastodon.rss.note.NotePublisher] と同じくその場で
 * 1 件ずつ送り、失敗しても再送しない。
 */
class ActorPublisher(
    private val notes: NoteStore,
    private val followers: FollowerStore,
    private val delivery: ActivityDelivery,
) {
    private val logger = LoggerFactory.getLogger(ActorPublisher::class.java)

    suspend fun delete(sender: ActorUrls): DeletedActor {
        val targets = followers.deliveryTargets(sender.username)

        val deletedNotes = notes.deleteByUsername(sender.username)
        val removedFollowers = followers.removeAccount(sender.username)

        val body = AppJson.encodeToString(
            DeleteActorActivity.serializer(),
            DeleteActorActivity(
                // 削除のたびに変える。同じ名前で作り直して再度消すと、
                // アクターの id から決めた固定の値では 2 回目が相手の重複判定で落ちる
                id = ActivityPubId("${sender.actorId}#delete-${UuidV7.generate()}"),
                actor = sender.actorId,
                to = listOf(ActivityStreamsIri.PUBLIC_AUDIENCE),
                target = sender.actorId,
            ),
        ).toByteArray()

        var delivered = 0
        targets.forEach { inbox ->
            when (val result = delivery.deliver(inbox = inbox, sender = sender, body = body)) {
                is DeliveryResult.Delivered -> delivered++

                // 再送しないので、届かなかったことはここに残っているものが唯一の手がかり
                is DeliveryResult.Failed -> logger.warn("配れなかった: ${sender.acct} → $inbox ${result.reason}")
            }
        }

        logger.info(
            "アクターの削除を配った: ${sender.acct} 宛先=${targets.size} 成功=$delivered " +
                "消した投稿=$deletedNotes 件 外したフォロワー=$removedFollowers 件",
        )

        return DeletedActor(
            deletedNotes = deletedNotes,
            removedFollowers = removedFollowers,
            targets = targets.size,
            delivered = delivered,
        )
    }
}

/**
 * @param deletedNotes 消した投稿の数
 * @param removedFollowers 外したフォロワーの数。`Accept` を返せていないものも含む
 * @param targets `Delete` を送った宛先の数
 * @param delivered そのうち相手が受け取った数
 */
data class DeletedActor(
    val deletedNotes: Int,
    val removedFollowers: Int,
    val targets: Int,
    val delivered: Int,
)
