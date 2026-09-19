package net.matsudamper.mastodon.rss.reaction

import java.time.Instant
import net.matsudamper.mastodon.rss.entity.PublicNoteId

/**
 * 投稿に届いた反応の置き先。
 *
 * [net.matsudamper.mastodon.rss.follower.FollowerStore] と同じく口だけを決めて、
 * 実装は `:backend` が repository に繋ぐ。
 *
 * 押した相手はアクター文書の URL で持つ。フォロワーとは限らず、こちらに記録の無い
 * 相手からも届く。
 */
interface ReactionStore {
    /**
     * 受け取った反応を記録する。
     *
     * 同じ相手の同じ反応と、同じアクティビティの送り直しでは増えない。
     *
     * @return 記録したら true。既にあるか、投稿が無ければ false
     */
    fun add(reaction: ReceivedReaction): Boolean

    /**
     * 取り消された反応を、元のアクティビティの id で消す。
     *
     * @param actorUri 押した相手。他人の反応を消されないよう、署名の持ち主で固定する
     * @return 消したら true
     */
    fun removeByActivityUri(
        actorUri: String,
        activityUri: String,
    ): Boolean

    /**
     * 取り消しに元のアクティビティの id が無いときに、投稿と絵文字で消す。
     *
     * @return 消したら true
     */
    fun removeByEmoji(
        notePublicId: PublicNoteId,
        actorUri: String,
        emoji: String,
    ): Boolean

    /**
     * 相手のアクターが押した反応を全部消す。相手が消えたときに使う。
     *
     * @return 消えた件数
     */
    fun removeActor(actorUri: String): Int
}

/**
 * 受け取った反応 1 件。
 *
 * @param activityUri 受け取った `Like` / `EmojiReact` の id
 * @param emoji 絵文字そのもの、またはカスタム絵文字の `:name:`。
 *   絵文字を伴わないお気に入りは空文字
 * @param emojiImageUrl カスタム絵文字の画像 URL。Unicode の絵文字とお気に入りでは null
 */
data class ReceivedReaction(
    val notePublicId: PublicNoteId,
    val actorUri: String,
    val activityUri: String,
    val emoji: String,
    val emojiImageUrl: String?,
    val receivedAt: Instant,
)
