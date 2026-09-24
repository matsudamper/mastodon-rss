package net.matsudamper.mastodon.rss.inbox

import net.matsudamper.mastodon.rss.activity.InboxActivity
import net.matsudamper.mastodon.rss.activitypub.LinkOrObject
import net.matsudamper.mastodon.rss.activitypub.id
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.favourite.FavouriteStore
import net.matsudamper.mastodon.rss.json.AppJson
import net.matsudamper.mastodon.rss.note.NoteUrls
import org.slf4j.LoggerFactory

/**
 * `Undo` のうち、お気に入りの取り消しの部分。
 *
 * 振り分けは [UndoHandler] が行う。`object` に元の `Like` が丸ごと入っていることも、
 * その id だけが入っていることもある。
 *
 * @param domain こちらのドメイン。対象がこちらの投稿かどうかの判断に使う
 */
class UndoFavouriteHandler(
    private val domain: String,
    private val favourites: FavouriteStore,
) {
    private val logger = LoggerFactory.getLogger(UndoFavouriteHandler::class.java)

    /**
     * お気に入りの取り消しとして処理する。
     *
     * @return お気に入りの取り消しだったら true。フォロー解除など別の取り消しなら false
     */
    fun handle(
        recipient: ActorUrls,
        verifiedSignerActorId: String,
        activity: InboxActivity,
    ): Boolean {
        return when (val undoObject = activity.target) {
            null -> false

            // id だけでは何の取り消しか分からない。記録しているお気に入りに当たれば
            // お気に入りの取り消しで、当たらなければ呼び出し側が別の取り消しとして扱う
            is LinkOrObject.Link -> removed(verifiedSignerActorId, undoObject.href, recipient)

            is LinkOrObject.Embedded -> removeEmbedded(recipient, verifiedSignerActorId, undoObject)
        }
    }

    private fun removeEmbedded(
        recipient: ActorUrls,
        verifiedSignerActorId: String,
        undoObject: LinkOrObject.Embedded,
    ): Boolean {
        val undoneActivity =
            runCatching { AppJson.decodeFromJsonElement(InboxActivity.serializer(), undoObject.json) }.getOrNull()
        if (undoneActivity == null) {
            logger.warn("Undo の object を読めなかった: ${recipient.acct} ← $verifiedSignerActorId")
            return false
        }

        if (undoneActivity.type != FavouriteHandler.LIKE_TYPE) return false

        val activityUri = undoneActivity.id
        if (activityUri != null && removed(verifiedSignerActorId, activityUri, recipient)) return true

        // 元のアクティビティの id が無いか、こちらが別の id で記録している。
        // 1 人が 1 つの投稿に持てるお気に入りは 1 つなので、投稿で引き当てる
        val notePublicId = undoneActivity.target?.id?.let { NoteUrls.publicIdOf(domain = domain, url = it) }
        if (notePublicId == null) {
            logger.info("取り消すお気に入りを引き当てられない: ${recipient.acct} ← $verifiedSignerActorId")
            return true
        }

        val removedByNote = favourites.removeByNote(notePublicId = notePublicId, actorUri = verifiedSignerActorId)

        if (removedByNote) {
            logger.info("お気に入りを取り消した: ${recipient.acct} ← $verifiedSignerActorId 投稿=${notePublicId.value}")
        } else {
            // 記録が無いのは異常ではない。押した後にこちらが投稿を消していても届く
            logger.info("取り消すお気に入りが記録に無い: ${recipient.acct} ← $verifiedSignerActorId 投稿=${notePublicId.value}")
        }

        return true
    }

    private fun removed(
        verifiedSignerActorId: String,
        activityUri: String,
        recipient: ActorUrls,
    ): Boolean {
        // 消せるのは署名した本人のお気に入りだけ。他人のお気に入りを消す Undo は
        // 名前を差し替えれば書けてしまう
        val removed = favourites.removeByActivityUri(actorUri = verifiedSignerActorId, activityUri = activityUri)
        if (removed) {
            logger.info("お気に入りを取り消した: ${recipient.acct} ← $verifiedSignerActorId id=$activityUri")
        }
        return removed
    }
}
