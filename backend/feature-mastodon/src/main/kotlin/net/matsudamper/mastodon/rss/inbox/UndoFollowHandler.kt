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
 * `Undo` は `Follow` 以外も取り消せるので、`object` が何だったのかを見てから消す。
 * 相手の実装によって、`object` に `Follow` が丸ごと埋まっていることも、
 * その id だけが入っていることもある。
 *
 * 埋まっている場合は `type` を見れば `Follow` だと分かる。id だけの場合は
 * 何のアクティビティの id なのか分からないので、こちらが記録している
 * `Follow` の id と一致するかどうかで判断する。一致しなければ何もしない。
 *
 * `Accept` は返さない。`Undo` に応答を返す決まりは無く、Mastodon も返さない。
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

            // id だけ。記録している Follow の id と突き合わせて絞る
            is LinkOrObject.Link -> target.href

            is LinkOrObject.Embedded -> embeddedFollowId(recipient, signer, target.json) ?: return
        }

        // 消せるのは署名した本人のフォローだけ。他人のフォローを消す Undo は
        // 名前を差し替えれば書けてしまうので、相手は署名の持ち主で固定する
        val removed = followers.remove(
            username = recipient.username,
            followerActorUri = signer,
            followActivityUri = followActivityUri,
        )

        if (removed) {
            logger.info("フォローを解除した: ${recipient.acct} ← $signer")
        } else {
            // 記録が無いのは異常ではない。こちらが Accept を返せなかったフォローや、
            // 既に Delete で消えた相手からも Undo は届く
            logger.info("解除するフォローが記録に無い: ${recipient.acct} ← $signer")
        }
    }

    /**
     * 埋め込まれた `object` を `Follow` として読み、その id を返す。
     *
     * `Follow` でなければ null を返して呼び出し側に何もさせない。
     * `Follow` だと分かっていて id が無い場合も null になるが、そのときは
     * [FollowerStore.remove] に null を渡して id を問わずに消す、とは区別が付かない。
     * 区別が要るほどの実装差は見ていないので、id が無ければ消さない側に倒す。
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

        // Undo{Like} などをフォロー解除として扱わない
        if (inner.type != FOLLOW_TYPE) {
            logger.info("Undo の対象が Follow ではないので何もしない: type=${inner.type} ${recipient.acct} ← $signer")
            return null
        }

        // 別のアクター宛の Follow を取り消す Undo は、こちらのフォローとは関係が無い
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
