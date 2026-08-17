package net.matsudamper.mastodon.rss.inbox

import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import net.matsudamper.mastodon.rss.activitypub.InboxActivity
import net.matsudamper.mastodon.rss.activitypub.LinkOrObject
import net.matsudamper.mastodon.rss.activitypub.OutgoingActivity
import net.matsudamper.mastodon.rss.activitypub.id
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.actor.RemoteActors
import net.matsudamper.mastodon.rss.delivery.ActivityDelivery
import net.matsudamper.mastodon.rss.delivery.DeliveryResult
import net.matsudamper.mastodon.rss.follower.FollowerStore
import net.matsudamper.mastodon.rss.json.AppJson
import org.slf4j.LoggerFactory

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
 * 記録してから `Accept` を返す。逆にすると、記録に失敗したときに相手だけが
 * フォローできたつもりになり、こちらには送り先が残らない。
 */
class FollowHandler(
    private val remoteActors: RemoteActors,
    private val delivery: ActivityDelivery,
    private val followers: FollowerStore,
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
        // 宛先の異なる Follow を投げ込まれても Accept を返さない
        val target = activity.target?.id
        if (target != recipient.actorId) {
            logger.warn("Follow の宛先が違うので Accept を返さない: object=$target 宛先=${recipient.actorId}")
            return
        }

        // id が無いと、送り直しと新しい `Follow` を区別できない
        val followActivityUri = activity.id
        if (followActivityUri == null) {
            logger.warn("Follow に id が無いので受け付けない: ${recipient.acct} ← $signer")
            return
        }

        val follower = remoteActors.findActor(signer)
        if (follower == null) {
            logger.warn("Follow に Accept を返せなかった: ${recipient.acct} ← $signer フォロワーのアクターを引けない")
            return
        }

        val recorded = runCatching {
            followers.record(
                username = recipient.username,
                follower = follower,
                followActivityUri = followActivityUri,
                receivedAt = Instant.now(),
            )
        }
        if (recorded.isFailure) {
            logger.warn(
                "Follow を記録できなかったので Accept を返さない: ${recipient.acct} ← $signer",
                recorded.exceptionOrNull(),
            )
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

        when (val result = delivery.deliver(inbox = follower.inbox, sender = recipient, body = body)) {
            is DeliveryResult.Delivered -> {
                // 記録に失敗しても相手は既にフォローできたつもりなので、例外は投げない
                runCatching {
                    followers.markAccepted(
                        username = recipient.username,
                        followerActorUri = signer,
                        followActivityUri = followActivityUri,
                        acceptedAt = Instant.now(),
                    )
                }.onFailure { failure ->
                    logger.warn("Accept は返せたがフォロワーとして記録できなかった: ${recipient.acct} ← $signer", failure)
                }

                logger.info("Follow に Accept を返した: ${recipient.acct} ← $signer")
            }

            is DeliveryResult.Failed -> {
                // 記録は残るが `Accept` 前の状態なのでフォロワーには数えない
                logger.warn("Follow に Accept を返せなかった: ${recipient.acct} ← $signer ${result.reason}")
            }
        }
    }

    private companion object {
        /**
         * `Accept` 自身の id。GET できる文書は無いのでフラグメントを付ける
         */
        fun acceptId(recipient: ActorUrls): String = "${recipient.actorId}#accepts/follows/${UUID.randomUUID()}"
    }
}
