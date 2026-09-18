package net.matsudamper.mastodon.rss.actor

import net.matsudamper.mastodon.rss.activity.ActivityStreamsIri
import net.matsudamper.mastodon.rss.activity.DeleteActorActivity
import net.matsudamper.mastodon.rss.activity.UpdateActorActivity
import net.matsudamper.mastodon.rss.crypto.UuidV7
import net.matsudamper.mastodon.rss.delivery.ActivityQueue
import net.matsudamper.mastodon.rss.delivery.QueuedActivityKind
import net.matsudamper.mastodon.rss.entity.ActivityPubId
import net.matsudamper.mastodon.rss.follower.FollowerStore
import net.matsudamper.mastodon.rss.json.AppJson
import net.matsudamper.mastodon.rss.note.NoteStore
import net.matsudamper.mastodon.rss.url.WebPageUrls
import org.slf4j.LoggerFactory

/**
 * アクター情報の更新と削除をフォロワーへ配る。
 *
 * 組み立てた `Update` / `Delete` は配信キューに投函する。送るのは配信ワーカーなので、
 * ここを抜けた時点では誰にも届いていない。
 */
class ActorPublisher(
    private val notes: NoteStore,
    private val followers: FollowerStore,
    private val queue: ActivityQueue,
    private val actorKey: ActorKey,
    private val feedLinks: StoredFeedLinks,
    private val profiles: StoredActorProfiles,
    private val webPages: WebPageUrls?,
) {
    private val logger = LoggerFactory.getLogger(ActorPublisher::class.java)

    /**
     * 今のアクター情報をフォロワーへ配る。
     *
     * 載せるのは Actor エンドポイントと同じ引き先から組み立てた文書。呼び出し元から
     * 中身を受け取ると、更新の保存とここの組み立てで別々に同じものを作ることになり、
     * 片方だけ変わったときに相手の表示だけが食い違う。
     */
    fun update(sender: ActorUrls) {
        val targets = followers.deliveryTargets(sender.username)
        val body = AppJson.encodeToString(
            UpdateActorActivity.serializer(),
            UpdateActorActivity(
                // 同じアクターを何度更新しても、相手の重複判定で落ちない id にする
                id = ActivityPubId("${sender.actorId}#update-${UuidV7.generate()}"),
                actor = sender.actorId,
                to = listOf(ActivityStreamsIri.PUBLIC_AUDIENCE),
                updatedActor = actorDocument(
                    urls = sender,
                    actorKey = actorKey,
                    feedLinks = feedLinks.find(sender.username),
                    profile = profiles.find(sender.username),
                    webPages = webPages,
                ),
            ),
        )

        val queued = queue.enqueue(
            kind = QueuedActivityKind.UPDATE_ACTOR,
            sender = sender,
            inboxes = targets,
            body = body,
            notePublicId = null,
        )

        logger.info("アクターの更新を投函した: ${sender.acct} 宛先=$queued")
    }

    /**
     * アクターを消して、消したことを配る。
     *
     * 配る前に配信先を控えてから、フォロワーと投稿の記録を消す。記録を残したまま配ると、
     * `Delete` を受けた相手が確かめに来たときにまだアクターの中身を返してしまう。
     */
    fun delete(sender: ActorUrls): DeletedActor {
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
        )

        val queued = queue.enqueue(
            kind = QueuedActivityKind.DELETE_ACTOR,
            sender = sender,
            inboxes = targets,
            body = body,
            notePublicId = null,
        )

        logger.info(
            "アクターの削除を投函した: ${sender.acct} 宛先=$queued " +
                "消した投稿=$deletedNotes 件 外したフォロワー=$removedFollowers 件",
        )

        return DeletedActor(
            deletedNotes = deletedNotes,
            removedFollowers = removedFollowers,
            targets = targets.size,
        )
    }
}

/**
 * @param deletedNotes 消した投稿の数
 * @param removedFollowers 外したフォロワーの数。`Accept` を返せていないものも含む
 * @param targets `Delete` を投函した宛先の数。届いたかどうかは配信ワーカーが決める
 */
data class DeletedActor(
    val deletedNotes: Int,
    val removedFollowers: Int,
    val targets: Int,
)
