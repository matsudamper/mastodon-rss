package net.matsudamper.mastodon.rss.inbox

import kotlinx.serialization.json.JsonObject
import net.matsudamper.mastodon.rss.activitypub.InboxActivity
import net.matsudamper.mastodon.rss.activitypub.LinkOrObject
import net.matsudamper.mastodon.rss.activitypub.id
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.follower.FollowerStore
import net.matsudamper.mastodon.rss.json.AppJson
import org.slf4j.LoggerFactory

/**
 * `Undo` を受けたときの処理。フォロー解除に使う。
 *
 * 相手の実装によって、`object` に `Follow` が丸ごと埋まっていることも、
 * その id だけが入っていることもある。id だけの場合は何のアクティビティの id なのか
 * 分からないので、記録している `Follow` の id と一致するときだけ消す。
 *
 * `Undo` に応答を返す決まりは無いので `Accept` は返さない。
 */
class UndoFollowHandler(
    private val followers: FollowerStore,
) : InboxActivityHandler {
    override val type: String = "Undo"

    private val logger = LoggerFactory.getLogger(UndoFollowHandler::class.java)

    override suspend fun handle(
        recipient: ActorUrls,
        signer: String,
        activity: InboxActivity,
        raw: JsonObject,
    ) {
        val followActivityUri = when (val target = activity.target) {
            null -> {
                logger.warn("Undo に object が無い: ${recipient.acct} ← $signer")
                return
            }

            is LinkOrObject.Link -> target.href

            is LinkOrObject.Embedded -> embeddedFollowId(recipient, signer, target.json) ?: return
        }

        // 他人のフォローを消す Undo は書けてしまうので、相手は署名の持ち主で固定する
        val removed = followers.remove(
            username = recipient.username,
            followerActorUri = signer,
            followActivityUri = followActivityUri,
        )

        if (removed) {
            logger.info("フォローを解除した: ${recipient.acct} ← $signer")
        } else {
            logger.info("解除するフォローが記録に無い: ${recipient.acct} ← $signer")
        }
    }

    /**
     * 埋め込まれた `object` を `Follow` として読み、その id を返す。
     *
     * `Follow` でない場合と id が無い場合は null を返し、呼び出し側に何もさせない
     */
    private fun embeddedFollowId(
        recipient: ActorUrls,
        signer: String,
        json: JsonObject,
    ): String? {
        val inner = runCatching { AppJson.decodeFromJsonElement(InboxActivity.serializer(), json) }.getOrNull()
        if (inner == null) {
            logger.warn("Undo の object を読めなかった: ${recipient.acct} ← $signer")
            return null
        }

        if (inner.type != FOLLOW_TYPE) {
            logger.info("Undo の対象が Follow ではないので何もしない: type=${inner.type} ${recipient.acct} ← $signer")
            return null
        }

        val followTarget = inner.target?.id
        if (followTarget != null && followTarget != recipient.actorId) {
            logger.info("Undo の対象が別のアクターへの Follow: object=$followTarget 宛先=${recipient.actorId}")
            return null
        }

        return inner.id
    }

    private companion object {
        const val FOLLOW_TYPE = "Follow"
    }
}
