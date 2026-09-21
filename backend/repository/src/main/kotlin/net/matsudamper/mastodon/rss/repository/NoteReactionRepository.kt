package net.matsudamper.mastodon.rss.repository

import java.time.Instant
import net.matsudamper.mastodon.rss.shared.PublicNoteId

/**
 * 投稿に届いた反応の読み書き。
 *
 * 相手のサーバーは押した時点でしか知らせてこないので、受け取った分をこちらに残す。
 * 数え直す口は無く、公開画面に出す数はここに溜まった行が全部になる。
 */
interface NoteReactionRepository {
    /**
     * 受け取った反応を記録する。
     *
     * 同じ相手の同じ反応や、同じアクティビティの送り直しでは行を増やさない。
     *
     * 上限は 2 つある。1 人の相手が 1 つの投稿に積める数と、1 つの投稿が持てる数。
     * 絵文字が違えば一意制約には当たらないので、前者が無いと 1 人で行を増やし続けられる。
     * 相手はアクターをいくつでも作れるので、後者が無いと人数ぶんだけ増やし続けられる。
     *
     * @return 記録したら true。既にあるか、どちらかの上限に達しているか、投稿が無ければ false
     */
    fun add(reaction: NewNoteReaction): Boolean

    /**
     * 取り消された反応を消す。
     *
     * @param actorUri 押した相手。他人の反応を消せないよう、署名を検証した相手で絞る
     * @return 消したら true
     */
    fun removeByActivityUri(
        actorUri: String,
        activityUri: String,
    ): Boolean

    /**
     * アクティビティの id が分からない取り消しのために、押した相手と絵文字で消す。
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
    fun removeByActor(actorUri: String): Int

    /**
     * 投稿ごとに、絵文字とその数を多い順に返す。
     *
     * 押した相手は返さない。公開画面に出すのは数だけで、誰が押したかは
     * 相手のサーバー側にしか出ない情報として扱う
     */
    fun countsByNotes(notePublicIds: Set<PublicNoteId>): Map<PublicNoteId, List<NoteReactionCount>>
}

/**
 * 記録する反応 1 件。
 *
 * @param activityUri 受け取った `Like` / `EmojiReact` の id
 * @param emoji 絵文字そのもの、またはカスタム絵文字の `:name:`。お気に入りは空文字
 * @param emojiImageUrl カスタム絵文字の画像 URL。Unicode の絵文字とお気に入りでは null
 */
data class NewNoteReaction(
    val notePublicId: PublicNoteId,
    val actorUri: String,
    val activityUri: String,
    val emoji: String,
    val emojiImageUrl: String?,
    val receivedAt: Instant,
)

/**
 * 同じ絵文字をまとめた数。
 *
 * @param emoji お気に入りは空文字
 * @param emojiImageUrl 同じ名前でも配信元のサーバーごとに画像が違う。どれか 1 つを返す
 */
data class NoteReactionCount(
    val emoji: String,
    val emojiImageUrl: String?,
    val count: Int,
)
