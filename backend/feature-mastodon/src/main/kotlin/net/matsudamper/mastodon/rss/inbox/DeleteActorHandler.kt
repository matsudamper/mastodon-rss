package net.matsudamper.mastodon.rss.inbox

import kotlinx.serialization.json.JsonObject
import net.matsudamper.mastodon.rss.activity.InboxActivity
import net.matsudamper.mastodon.rss.activitypub.id
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.follower.FollowerStore
import net.matsudamper.mastodon.rss.reaction.ReactionStore
import org.slf4j.LoggerFactory

/**
 * `Delete` を受けたときの処理。アカウントが消えたフォロワーを掃除する。
 *
 * `Delete` は投稿の削除にも使われるので、`object` が送り主自身を指しているものだけを
 * アカウントの削除として扱う。Mastodon はアカウントの削除と引っ越しのどちらでも
 * この形で送ってくる。
 *
 * 掃除しないと、消えたアカウントの inbox に投稿を送り続けることになる。
 *
 * ここに届くのは署名を検証できたものだけ。相手が既に消えていてアクター文書を
 * 引けなくても、フォローを受けたときに読んだ鍵が残っているので検証は通る。
 * 記録の無い相手からの `Delete` は [InboxService] が検証の手前で落とすが、
 * フォロワーではないので掃除するものも無い。
 */
class DeleteActorHandler(
    private val followers: FollowerStore,
    private val reactions: ReactionStore,
) : InboxActivityHandler {
    override val type: String = "Delete"

    private val logger = LoggerFactory.getLogger(DeleteActorHandler::class.java)

    override suspend fun handle(
        recipient: ActorUrls,
        verifiedSignerActorId: String,
        activity: InboxActivity,
        rawActivityJson: JsonObject,
    ) {
        val deleteObjectId = activity.target?.id
        if (deleteObjectId == null) {
            logger.warn("Delete に object が無い: ${recipient.acct} ← $verifiedSignerActorId")
            return
        }

        if (deleteObjectId != verifiedSignerActorId) {
            logger.info("Delete の対象がアクター自身ではないので何もしない: object=$deleteObjectId 署名者=$verifiedSignerActorId")
            return
        }

        // こちらのどのアカウントをフォローしていたかに関わらず全部消える。
        // 宛先のアカウントだけを消すと、同じ相手が他のアカウントをフォローしていた分が
        // 残り、消えた相手に送り続けることになる
        val removed = followers.removeRemoteActor(deleteObjectId)

        // 消えた相手が押した反応も一緒に消す。残すと、もう居ない相手の
        // お気に入りやスタンプが公開画面に出たままになる
        val removedReactions = reactions.removeActor(deleteObjectId)

        logger.info(
            "アクターが削除されたので記録から外した: $deleteObjectId " +
                "解除したフォロー=$removed 件 取り消した反応=$removedReactions 件",
        )
    }
}
