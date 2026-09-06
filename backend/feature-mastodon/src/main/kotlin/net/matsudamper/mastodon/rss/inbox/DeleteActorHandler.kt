package net.matsudamper.mastodon.rss.inbox

import kotlinx.serialization.json.JsonObject
import net.matsudamper.mastodon.rss.activity.InboxActivity
import net.matsudamper.mastodon.rss.activitypub.id
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.follower.FollowerStore
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
 * ここに届くのは署名を検証できたものだけで、それは相手のアクター文書がまだ
 * 引ける場合に限られる。既に消えていて鍵を取れない `Delete` は
 * [InboxService] が検証の手前で落としているので、掃除はできない。
 * その場合のフォロワーは、配信が失敗し続けるものとして残る。
 */
class DeleteActorHandler(
    private val followers: FollowerStore,
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

        logger.info("アクターが削除されたのでフォロワーから外した: $deleteObjectId 解除したフォロー=$removed 件")
    }
}
