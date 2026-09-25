package net.matsudamper.mastodon.rss.inbox

import java.time.Instant
import kotlin.time.toJavaDuration
import net.matsudamper.mastodon.rss.activity.InboxActivity
import net.matsudamper.mastodon.rss.activitypub.LinkOrObject
import net.matsudamper.mastodon.rss.activitypub.id
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.favourite.FavouriteStore
import net.matsudamper.mastodon.rss.json.AppJson
import net.matsudamper.mastodon.rss.note.NoteUrls
import org.slf4j.LoggerFactory

/**
 * 受け取った `Like` アクティビティ自身の `id` は記録していないので、`object` にその
 * `id` だけが入った `Undo` ではお気に入りを取り消せない。Mastodon も同じで、この形の
 * 取り消しはお気に入りとしては扱わない。
 */
class UndoFavouriteHandler(
    private val domain: String,
    private val favourites: FavouriteStore,
    private val earlyUndoneLikes: EarlyUndoneLikes,
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
            is LinkOrObject.Link -> false
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

        val notePublicId = undoneActivity.target?.id?.let { NoteUrls.publicIdOf(domain = domain, url = it) }
        if (notePublicId == null) {
            logger.info("取り消すお気に入りを引き当てられない: ${recipient.acct} ← $verifiedSignerActorId")
            return true
        }

        val removed = favourites.removeByNote(notePublicId = notePublicId, actorUri = verifiedSignerActorId)
        if (removed) {
            logger.info("お気に入りを取り消した: ${recipient.acct} ← $verifiedSignerActorId 投稿=${notePublicId.value}")
            return true
        }

        val likeId = undoneActivity.id
        if (likeId != null) {
            rememberEarlyUndone(actorUri = verifiedSignerActorId, activityUri = likeId)
        }
        logger.info("取り消すお気に入りが記録に無い: ${recipient.acct} ← $verifiedSignerActorId 投稿=${notePublicId.value}")
        return true
    }

    /**
     * @param activityUri `Undo` の `object` が指していた `Like` アクティビティの `id`。
     *   後から同じ `id` の `Like` が届いても記録しない
     */
    fun rememberEarlyUndone(
        actorUri: String,
        activityUri: String,
    ) {
        earlyUndoneLikes.remember(
            actorUri = actorUri,
            activityUri = activityUri,
            expiresAt = Instant.now() + EarlyUndoneLikes.TTL.toJavaDuration(),
        )
    }
}
