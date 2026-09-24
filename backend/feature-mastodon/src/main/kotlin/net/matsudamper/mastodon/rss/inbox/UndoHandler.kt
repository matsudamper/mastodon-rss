package net.matsudamper.mastodon.rss.inbox

import kotlinx.serialization.json.JsonObject
import net.matsudamper.mastodon.rss.activity.InboxActivity
import net.matsudamper.mastodon.rss.actor.ActorUrls

/**
 * `Undo` を受けたときの振り分け。
 *
 * `Undo` は取り消せるものが 1 つではないので、`object` が何だったのかで
 * 渡し先を決める。`type` でハンドラを引き当てる [InboxService] からは
 * `Undo` が 1 つに見えるようにする。
 *
 * お気に入りから先に見る。`object` に id だけが入っていると何の取り消しか分からず、
 * 記録に当たるかどうかでしか判断できない。フォローの解除は記録が無ければ
 * 何もしないので、お気に入りの取り消しを渡しても消えるものは無い。
 */
class UndoHandler(
    private val favourites: UndoFavouriteHandler,
    private val follows: UndoFollowHandler,
) : InboxActivityHandler {
    override val type: String = "Undo"

    override suspend fun handle(
        recipient: ActorUrls,
        verifiedSignerActorId: String,
        activity: InboxActivity,
        rawActivityJson: JsonObject,
    ) {
        val undoneFavourite = favourites.handle(
            recipient = recipient,
            verifiedSignerActorId = verifiedSignerActorId,
            activity = activity,
        )
        if (undoneFavourite) return

        follows.handle(
            recipient = recipient,
            verifiedSignerActorId = verifiedSignerActorId,
            activity = activity,
        )
    }
}
