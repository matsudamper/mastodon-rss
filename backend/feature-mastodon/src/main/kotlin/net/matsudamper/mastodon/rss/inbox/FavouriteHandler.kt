package net.matsudamper.mastodon.rss.inbox

import java.time.Instant
import kotlinx.serialization.json.JsonObject
import net.matsudamper.mastodon.rss.activity.InboxActivity
import net.matsudamper.mastodon.rss.activitypub.id
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.actor.RemoteActors
import net.matsudamper.mastodon.rss.favourite.FavouriteStore
import net.matsudamper.mastodon.rss.favourite.FavouriteStore.ReceivedFavourite
import net.matsudamper.mastodon.rss.note.NoteUrls
import org.slf4j.LoggerFactory

/**
 * Mastodon のお気に入りは `content` を持たない `Like` で届く。Misskey は押した
 * 絵文字を `content` に載せた `Like` を送ってくるが、絵文字は見ずにこれも
 * お気に入りとして数える。Mastodon も同じ扱いをする。
 */
class FavouriteHandler(
    private val domain: String,
    private val remoteActors: RemoteActors,
    private val favourites: FavouriteStore,
    private val earlyUndoneLikes: EarlyUndoneLikes,
) : InboxActivityHandler {
    override val type: String = LIKE_TYPE

    private val logger = LoggerFactory.getLogger(FavouriteHandler::class.java)

    override suspend fun handle(
        recipient: ActorUrls,
        verifiedSignerActorId: String,
        activity: InboxActivity,
        rawActivityJson: JsonObject,
    ) {
        val targetUrl = activity.target?.id
        if (targetUrl == null) {
            logger.warn("Like に object が無い: ${recipient.acct} ← $verifiedSignerActorId")
            return
        }

        val notePublicId = NoteUrls.publicIdOf(domain = domain, url = targetUrl)
        if (notePublicId == null) {
            logger.info("Like の対象がこちらの投稿ではないので何もしない: object=$targetUrl")
            return
        }

        val activityUri = activity.id
        if (activityUri != null && earlyUndoneLikes.contains(actorUri = verifiedSignerActorId, activityUri = activityUri)) {
            logger.info("先に取り消しが届いた Like なので記録しない: ${recipient.acct} ← $verifiedSignerActorId id=$activityUri")
            return
        }

        // 鍵ごと残さないと、相手が消えた後の `Delete` を検証できず、
        // 居ない相手のお気に入りが公開画面に出たままになる
        val actor = remoteActors.findActor(verifiedSignerActorId)
        if (actor == null) {
            logger.warn("Like の押し手のアクター文書を引けないので受け付けない: ${recipient.acct} ← $verifiedSignerActorId")
            return
        }

        val recorded = favourites.add(
            ReceivedFavourite(
                notePublicId = notePublicId,
                actor = actor,
                receivedAt = Instant.now(),
            ),
        )

        if (recorded) {
            logger.info("お気に入りを記録した: ${recipient.acct} ← $verifiedSignerActorId 投稿=${notePublicId.value}")
        } else {
            logger.info("お気に入りを記録しなかった。記録済みか投稿が無い: ${recipient.acct} ← $verifiedSignerActorId 投稿=${notePublicId.value}")
        }
    }

    companion object {
        const val LIKE_TYPE: String = "Like"
    }
}
