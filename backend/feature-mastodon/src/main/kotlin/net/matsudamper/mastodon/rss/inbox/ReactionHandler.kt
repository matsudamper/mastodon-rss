package net.matsudamper.mastodon.rss.inbox

import java.time.Instant
import kotlinx.serialization.json.JsonObject
import net.matsudamper.mastodon.rss.activity.InboxActivity
import net.matsudamper.mastodon.rss.activitypub.id
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.note.NoteStore
import net.matsudamper.mastodon.rss.note.NoteUrls
import net.matsudamper.mastodon.rss.reaction.ReactionEmoji
import net.matsudamper.mastodon.rss.reaction.ReactionEmojiTags
import net.matsudamper.mastodon.rss.reaction.ReactionStore
import net.matsudamper.mastodon.rss.reaction.ReceivedReaction
import org.slf4j.LoggerFactory

/**
 * 投稿への反応を受けたときの処理。お気に入りと絵文字のスタンプを記録する。
 *
 * 同じ形のアクティビティが 2 つの `type` で届く。Mastodon のお気に入りと
 * Misskey のスタンプはどちらも `Like` で、Misskey はスタンプを `EmojiReact` でも
 * 送ってくる（Pleroma も同じ）。違いは `type` だけで、対象の投稿も絵文字も
 * 同じ場所に入っているので、[type] を変えた 2 つを登録して同じ処理に流す。
 *
 * 応答は返さない。`Like` に返す決まりは無く、Mastodon も返さない。
 *
 * @param type 受け持つアクティビティの綴り。`Like` か `EmojiReact`
 * @param domain こちらのドメイン。対象がこちらの投稿かどうかの判断に使う
 */
class ReactionHandler(
    override val type: String,
    private val domain: String,
    private val notes: NoteStore,
    private val reactions: ReactionStore,
) : InboxActivityHandler {
    private val logger = LoggerFactory.getLogger(ReactionHandler::class.java)

    override suspend fun handle(
        recipient: ActorUrls,
        verifiedSignerActorId: String,
        activity: InboxActivity,
        rawActivityJson: JsonObject,
    ) {
        // 取り消しは元のアクティビティの id で指してくる。id が無いものを記録すると、
        // 取り消しと結び付けられないまま残る
        val activityUri = activity.id
        if (activityUri == null) {
            logger.warn("$type に id が無いので受け付けない: ${recipient.acct} ← $verifiedSignerActorId")
            return
        }

        val targetUrl = activity.target?.id
        if (targetUrl == null) {
            logger.warn("$type に object が無い: ${recipient.acct} ← $verifiedSignerActorId")
            return
        }

        val notePublicId = NoteUrls.publicIdOf(domain = domain, url = targetUrl)
        if (notePublicId == null) {
            logger.info("$type の対象がこちらの投稿ではないので何もしない: object=$targetUrl")
            return
        }

        // 面識の無いサーバーからも届く。宛先のアカウントの投稿に絞らないと、
        // 別のアカウントの投稿への反応がこのアカウントの画面に出る
        val note = notes.find(notePublicId)
        if (note == null || note.username != recipient.username) {
            logger.info("$type の対象が宛先のアカウントの投稿ではないので何もしない: object=$targetUrl 宛先=${recipient.acct}")
            return
        }

        val emoji = ReactionEmoji.of(activity.content)
        if (emoji == null) {
            logger.info("$type の絵文字が長すぎるので受け付けない: ${recipient.acct} ← $verifiedSignerActorId")
            return
        }

        val recorded = reactions.add(
            ReceivedReaction(
                notePublicId = notePublicId,
                actorUri = verifiedSignerActorId,
                activityUri = activityUri,
                emoji = emoji,
                emojiImageUrl = ReactionEmojiTags.imageUrl(rawActivityJson = rawActivityJson, emoji = emoji),
                receivedAt = Instant.now(),
            ),
        )

        if (recorded) {
            logger.info("$type を記録した: ${recipient.acct} ← $verifiedSignerActorId 絵文字=$emoji")
        } else {
            // 同じ反応の送り直しで届く。相手から見ると 1 回しか押していない
            logger.info("$type は記録済みなので増やさない: ${recipient.acct} ← $verifiedSignerActorId 絵文字=$emoji")
        }
    }

    companion object {
        /** Mastodon のお気に入りと、Misskey のスタンプ */
        const val LIKE_TYPE: String = "Like"

        /** Misskey と Pleroma のスタンプ */
        const val EMOJI_REACT_TYPE: String = "EmojiReact"
    }
}
