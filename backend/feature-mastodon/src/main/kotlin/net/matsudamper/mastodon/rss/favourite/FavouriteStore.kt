package net.matsudamper.mastodon.rss.favourite

import java.time.Instant
import net.matsudamper.mastodon.rss.actor.RemoteActor
import net.matsudamper.mastodon.rss.entity.PublicNoteId

/**
 * 投稿に届いたお気に入りの置き先。
 *
 * [net.matsudamper.mastodon.rss.follower.FollowerStore] と同じく口だけを決めて、
 * 実装は `:backend` が repository に繋ぐ。
 *
 * 押した相手はフォロワーとは限らない。それでも相手のアクターを鍵ごと記録するので、
 * 相手が消えた後の `Delete` を検証して [removeActor] まで辿り着ける。
 */
interface FavouriteStore {
    /**
     * 受け取ったお気に入りを記録する。
     *
     * 同じ相手が同じ投稿に押し直したときと、同じアクティビティの送り直しでは増えない。
     *
     * @return 記録したら true。既にあるか、投稿が無ければ false
     */
    fun add(favourite: ReceivedFavourite): Boolean

    /**
     * 取り消されたお気に入りを、元のアクティビティの id で消す。
     *
     * @param actorUri 押した相手。他人のお気に入りを消されないよう、署名の持ち主で固定する
     * @return 消したら true
     */
    fun removeByActivityUri(
        actorUri: String,
        activityUri: String,
    ): Boolean

    /**
     * 取り消しに元のアクティビティの id が無いときに、投稿と押した相手で消す。
     *
     * @return 消したら true
     */
    fun removeByNote(
        notePublicId: PublicNoteId,
        actorUri: String,
    ): Boolean

    /**
     * 相手のアクターが押したお気に入りを全部消す。相手が消えたときに使う。
     *
     * @return 消えた件数
     */
    fun removeActor(actorUri: String): Int

    /**
     * お気に入りを押した相手の公開鍵の PEM を返す。記録が無ければ null。
     *
     * 相手が消えてアクター文書を引けなくなったときの、鍵の引き先になる
     */
    fun findPublicKeyPem(actorUri: String): String?
}

/**
 * 受け取ったお気に入り 1 件。
 *
 * @param actor 押した相手。押された時点のアクター文書から読んだもの
 * @param activityUri 受け取った `Like` の id
 */
data class ReceivedFavourite(
    val notePublicId: PublicNoteId,
    val actor: RemoteActor,
    val activityUri: String,
    val receivedAt: Instant,
)
