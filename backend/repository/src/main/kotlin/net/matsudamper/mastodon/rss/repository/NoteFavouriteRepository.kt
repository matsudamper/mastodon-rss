package net.matsudamper.mastodon.rss.repository

import java.time.Instant
import net.matsudamper.mastodon.rss.shared.PublicNoteId

/**
 * 投稿に届いたお気に入りの読み書き。
 *
 * 相手のサーバーは押した時点でしか知らせてこないので、受け取った分をこちらに残す。
 * 数え直す口は無く、公開画面に出す数はここに溜まった行が全部になる。
 */
interface NoteFavouriteRepository {
    /**
     * 受け取ったお気に入りを記録する。
     *
     * 同じ相手が同じ投稿に押し直したときと、同じアクティビティの送り直しでは行を増やさない。
     * 1 つの投稿が持てる数にも上限がある。
     *
     * @return 記録したら true。既にあるか、上限に達しているか、投稿が無ければ false
     */
    fun add(favourite: NewNoteFavourite): Boolean

    /**
     * 取り消されたお気に入りを消す。
     *
     * @param actorUri 押した相手。他人のお気に入りを消せないよう、署名を検証した相手で絞る
     * @return 消したら true
     */
    fun removeByActivityUri(
        actorUri: String,
        activityUri: String,
    ): Boolean

    /**
     * アクティビティの id が分からない取り消しのために、投稿と押した相手で消す。
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
    fun removeByActor(actorUri: String): Int

    /**
     * お気に入りを押した相手の公開鍵の PEM を返す。記録が無ければ null。
     *
     * 相手のサーバーから引けなくなった鍵の代わりに使う。フォロワーでない相手も
     * 押した時点の鍵を残してあるので、消えた後の `Delete` を検証できる
     */
    fun findPublicKeyPem(actorUri: String): String?

    /**
     * 投稿ごとのお気に入りの数。1 件も無い投稿は含めない
     */
    fun countsByNotes(notePublicIds: Set<PublicNoteId>): Map<PublicNoteId, Int>
}

/**
 * 記録するお気に入り 1 件。
 *
 * @param actor 押した相手。フォロワーとは限らないが、消えた後の `Delete` を
 *   検証できるよう鍵ごと残す
 * @param activityUri 受け取った `Like` の id
 */
data class NewNoteFavourite(
    val notePublicId: PublicNoteId,
    val actor: NewRemoteActor,
    val activityUri: String,
    val receivedAt: Instant,
)
