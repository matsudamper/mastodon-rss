package net.matsudamper.mastodon.rss.inbox

import kotlinx.serialization.json.JsonObject
import net.matsudamper.mastodon.rss.activitypub.InboxActivity
import net.matsudamper.mastodon.rss.activitypub.LinkOrObject
import net.matsudamper.mastodon.rss.activitypub.OutgoingActivity
import net.matsudamper.mastodon.rss.activitypub.id
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.actor.RemoteActors
import net.matsudamper.mastodon.rss.delivery.ActivityDelivery
import net.matsudamper.mastodon.rss.delivery.DeliveryResult
import net.matsudamper.mastodon.rss.json.AppJson
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * `Follow` を受けたときの処理。
 *
 * ActivityPub のフォローは、相手が `Follow` を送ってきた時点では成立していない。
 * こちらが `Accept` を相手の inbox に返して初めて確定する。返さないと
 * Mastodon の画面ではフォローボタンが保留のまま戻らない。
 *
 * 送信は inbox の応答を返す前に行う。配信キューが無いので、ここで送らないと
 * 送る機会が無い。相手のサーバーが応答しない場合に備えて、
 * HTTP クライアント側にタイムアウトを入れてある。
 *
 * フォロワーの記録はまだしない。オンメモリにも持たず、`Accept` を返すだけ。
 * 永続化は TODO.md の Phase 3。
 */
class FollowHandler(
    private val remoteActors: RemoteActors,
    private val delivery: ActivityDelivery,
) : InboxActivityHandler {
    override val type: String = "Follow"

    private val logger = LoggerFactory.getLogger(FollowHandler::class.java)

    /**
     * `Accept` を組み立てて相手の inbox に返す。
     *
     * @param recipient フォローされたこちらのアクター
     * @param signer `Follow` を送ってきた相手のアクター id。
     *   署名を検証した結果の持ち主で、自称ではない
     * @param raw 受け取った `Follow` そのもの。[OutgoingActivity.target] に丸ごと入れる。
     *   id だけを返すと、元のアクティビティを保持していない実装では突き合わせができない
     */
    override suspend fun handle(
        recipient: ActorUrls,
        signer: String,
        activity: InboxActivity,
        raw: JsonObject,
    ) {
        // 宛先の異なる Follow をこちらの inbox に投げ込むことはできる。
        // 中身を見ずに Accept を返すと、フォローしていないアクターの
        // フォローが成立したように相手に見える
        val target = activity.target?.id
        if (target != recipient.actorId) {
            logger.warn("Follow の宛先が違うので Accept を返さない: object=$target 宛先=${recipient.actorId}")
            return
        }

        val inbox = remoteActors.findInbox(signer)
        if (inbox == null) {
            logger.warn("Follow に Accept を返せなかった: ${recipient.acct} ← $signer フォロワーの inbox を引けない")
            return
        }

        val accept =
            OutgoingActivity(
                id = acceptId(recipient),
                type = OutgoingActivity.TYPE_ACCEPT,
                actor = recipient.actorId,
                target = LinkOrObject.Embedded(raw),
            )

        val body = AppJson.encodeToString(OutgoingActivity.serializer(), accept).toByteArray()

        when (val result = delivery.deliver(inbox = inbox, sender = recipient, body = body)) {
            is DeliveryResult.Delivered -> {
                logger.info("Follow に Accept を返した: ${recipient.acct} ← $signer")
            }

            is DeliveryResult.Failed -> {
                // 相手から見るとフォローが保留のまま残る。再送はしないので、
                // 何が起きたのかはここに残っているものが唯一の手がかりになる
                logger.warn("Follow に Accept を返せなかった: ${recipient.acct} ← $signer ${result.reason}")
            }
        }
    }

    private companion object {
        /**
         * `Accept` 自身の id。
         *
         * アクター id にフラグメントを付けた形にする。相手はこの URL を取りに来ないが、
         * 独立したパスにすると「GET できる文書がある」と読める形になり、
         * 実際には返せないものを配ることになる。Mastodon も同じ作りで送ってくる。
         */
        fun acceptId(recipient: ActorUrls): String = "${recipient.actorId}#accepts/follows/${UUID.randomUUID()}"
    }
}
