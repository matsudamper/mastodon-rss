package net.matsudamper.mastodon.rss.inbox

import net.matsudamper.mastodon.rss.activity.InboxActivity
import net.matsudamper.mastodon.rss.activitypub.LinkOrObject
import net.matsudamper.mastodon.rss.activitypub.id
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.json.AppJson
import net.matsudamper.mastodon.rss.note.NoteUrls
import net.matsudamper.mastodon.rss.reaction.ReactionEmoji
import net.matsudamper.mastodon.rss.reaction.ReactionStore
import org.slf4j.LoggerFactory

/**
 * `Undo` のうち、お気に入りとスタンプの取り消しの部分。
 *
 * 振り分けは [UndoHandler] が行う。`object` に元の `Like` / `EmojiReact` が
 * 丸ごと入っていることも、その id だけが入っていることもある。
 *
 * Misskey はスタンプを押し替えると、前のスタンプの取り消しを送ってから
 * 新しいものを送ってくる。取り消しを落とすと、1 人が押したスタンプが
 * 押し替えた数だけ並ぶ。
 *
 * @param domain こちらのドメイン。対象がこちらの投稿かどうかの判断に使う
 */
class UndoReactionHandler(
    private val domain: String,
    private val reactions: ReactionStore,
) {
    private val logger = LoggerFactory.getLogger(UndoReactionHandler::class.java)

    /**
     * 反応の取り消しとして処理する。
     *
     * @return 反応の取り消しだったら true。フォロー解除など別の取り消しなら false
     */
    fun handle(
        recipient: ActorUrls,
        verifiedSignerActorId: String,
        activity: InboxActivity,
    ): Boolean {
        return when (val undoObject = activity.target) {
            null -> false

            // id だけでは何の取り消しか分からない。記録している反応に当たれば
            // 反応の取り消しで、当たらなければ呼び出し側が別の取り消しとして扱う
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

        if (undoneActivity.type != ReactionHandler.LIKE_TYPE && undoneActivity.type != ReactionHandler.EMOJI_REACT_TYPE) {
            return false
        }

        val activityUri = undoneActivity.id
        if (activityUri != null && removed(verifiedSignerActorId, activityUri, recipient)) return true

        // 元のアクティビティの id が無いか、こちらが別の id で記録している。
        // 相手は同じ投稿に同じ絵文字を二度は押せないので、その 2 つで引き当てる
        val notePublicId = undoneActivity.target?.id?.let { NoteUrls.publicIdOf(domain = domain, url = it) }
        val emoji = ReactionEmoji.of(undoneActivity.content)
        if (notePublicId == null || emoji == null) {
            logger.info("取り消す反応を引き当てられない: ${recipient.acct} ← $verifiedSignerActorId")
            return true
        }

        val removedByEmoji = reactions.removeByEmoji(
            notePublicId = notePublicId,
            actorUri = verifiedSignerActorId,
            emoji = emoji,
        )

        if (removedByEmoji) {
            logger.info("反応を取り消した: ${recipient.acct} ← $verifiedSignerActorId 絵文字=$emoji")
        } else {
            // 記録が無いのは異常ではない。押した後にこちらが投稿を消していても届く
            logger.info("取り消す反応が記録に無い: ${recipient.acct} ← $verifiedSignerActorId 絵文字=$emoji")
        }

        return true
    }

    private fun removed(
        verifiedSignerActorId: String,
        activityUri: String,
        recipient: ActorUrls,
    ): Boolean {
        // 消せるのは署名した本人の反応だけ。他人の反応を消す Undo は
        // 名前を差し替えれば書けてしまう
        val removed = reactions.removeByActivityUri(actorUri = verifiedSignerActorId, activityUri = activityUri)
        if (removed) {
            logger.info("反応を取り消した: ${recipient.acct} ← $verifiedSignerActorId id=$activityUri")
        }
        return removed
    }
}
