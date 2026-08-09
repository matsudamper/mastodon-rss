package net.matsudamper.mastodon.rss.inbox

import kotlinx.serialization.json.JsonObject
import net.matsudamper.mastodon.rss.activitypub.LinkOrObject
import net.matsudamper.mastodon.rss.activitypub.OutgoingActivity
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.actor.RemoteActors
import net.matsudamper.mastodon.rss.delivery.ActivityDelivery
import net.matsudamper.mastodon.rss.delivery.DeliveryResult
import net.matsudamper.mastodon.rss.json.AppJson
import java.util.UUID

/**
 * `Follow` を受けたときの処理。
 *
 * ActivityPub のフォローは、相手が `Follow` を送ってきた時点では成立していない。
 * こちらが `Accept` を相手の inbox に返して初めて確定する。返さないと
 * Mastodon の画面ではフォローボタンが保留のまま戻らない。
 *
 * フォロワーの記録はまだしない。オンメモリにも持たず、`Accept` を返すだけ。
 * 永続化は TODO.md の Phase 3。
 */
class FollowHandler(
    private val remoteActors: RemoteActors,
    private val delivery: ActivityDelivery,
) {
    /**
     * `Accept` を組み立てて相手の inbox に返す。
     *
     * @param recipient フォローされたこちらのアクター
     * @param follower `Follow` を送ってきた相手のアクター id。
     *   署名を検証した結果の持ち主で、自称ではない
     * @param follow 受け取った `Follow` そのもの。[OutgoingActivity.target] に丸ごと入れる。
     *   id だけを返すと、元のアクティビティを保持していない実装では突き合わせができない
     */
    suspend fun accept(
        recipient: ActorUrls,
        follower: String,
        follow: JsonObject,
    ): DeliveryResult {
        val inbox =
            remoteActors.findInbox(follower)
                ?: return DeliveryResult.Failed("フォロワーの inbox を引けない: $follower")

        val accept =
            OutgoingActivity(
                id = acceptId(recipient),
                type = OutgoingActivity.TYPE_ACCEPT,
                actor = recipient.actorId,
                target = LinkOrObject.Embedded(follow),
            )

        val body = AppJson.encodeToString(OutgoingActivity.serializer(), accept).toByteArray()

        return delivery.deliver(inbox = inbox, sender = recipient, body = body)
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
