package net.matsudamper.mastodon.rss.inbox

import kotlinx.serialization.json.JsonObject
import net.matsudamper.mastodon.rss.activitypub.InboxActivity
import net.matsudamper.mastodon.rss.activitypub.id
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.follower.FollowerStore
import org.slf4j.LoggerFactory

/**
 * `Delete` を受けたときの処理。アカウントが消えたフォロワーを掃除する。
 *
 * `Delete` は投稿の削除にも使われるので、`object` が送り主自身を指しているものだけを
 * アカウントの削除として扱う。
 *
 * ここに届くのは署名を検証できたものだけで、それは相手のアクター文書がまだ
 * 引ける場合に限られる。鍵を取れない `Delete` は [InboxService] が検証の手前で
 * 落とすので掃除できず、そのフォロワーは配信が失敗し続けるものとして残る。
 */
class DeleteActorHandler(
    private val followers: FollowerStore,
) : InboxActivityHandler {
    override val type: String = "Delete"

    private val logger = LoggerFactory.getLogger(DeleteActorHandler::class.java)

    override suspend fun handle(
        recipient: ActorUrls,
        signer: String,
        activity: InboxActivity,
        raw: JsonObject,
    ) {
        val deleted = activity.target?.id
        if (deleted == null) {
            logger.warn("Delete に object が無い: ${recipient.acct} ← $signer")
            return
        }

        if (deleted != signer) {
            logger.info("Delete の対象がアクター自身ではないので何もしない: object=$deleted 署名者=$signer")
            return
        }

        // 宛先のアカウント分だけを消すと、同じ相手が他のアカウントをフォローしていた分が
        // 残り、消えた相手に送り続けることになる
        val removed = followers.removeRemoteActor(deleted)

        logger.info("アクターが削除されたのでフォロワーから外した: $deleted 解除したフォロー=$removed 件")
    }
}
