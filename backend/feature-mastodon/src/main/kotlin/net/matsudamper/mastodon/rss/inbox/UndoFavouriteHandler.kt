package net.matsudamper.mastodon.rss.inbox

import net.matsudamper.mastodon.rss.activity.InboxActivity
import net.matsudamper.mastodon.rss.activitypub.LinkOrObject
import net.matsudamper.mastodon.rss.activitypub.id
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.favourite.FavouriteStore
import net.matsudamper.mastodon.rss.json.AppJson
import net.matsudamper.mastodon.rss.note.NoteUrls
import org.slf4j.LoggerFactory

class UndoFavouriteHandler(
    private val domain: String,
    private val favourites: FavouriteStore,
) {
    private val logger = LoggerFactory.getLogger(UndoFavouriteHandler::class.java)

    /**
     * @return お気に入りの取り消しだったら true。フォロー解除など別の取り消しなら false
     */
    fun handle(
        recipient: ActorUrls,
        verifiedSignerActorId: String,
        activity: InboxActivity,
    ): Boolean {
        return when (val undoObject = activity.target) {
            null -> false
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
            logger.info("取り消すお気に入りが記録に無い: ${recipient.acct} ← $verifiedSignerActorId 投稿=${notePublicId.value}")
        }

        return true
    }

    private fun removed(
        verifiedSignerActorId: String,
        activityUri: String,
        recipient: ActorUrls,
    ): Boolean {
        val removed = favourites.removeByActivityUri(actorUri = verifiedSignerActorId, activityUri = activityUri)
        if (removed) {
            logger.info("お気に入りを取り消した: ${recipient.acct} ← $verifiedSignerActorId id=$activityUri")
        }
        return removed
    }
}
