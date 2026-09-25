package net.matsudamper.mastodon.rss.repository

import java.time.Instant
import net.matsudamper.mastodon.rss.shared.PublicNoteId

interface NoteFavouriteRepository {
    /**
     * 同じ相手が同じ投稿に押し直しても行を増やさない。
     *
     * @return 記録したら true。既にあるか、投稿が無ければ false
     */
    fun add(favourite: NewNoteFavourite): Boolean

    /**
     * @param actorUri 押した相手。他人のお気に入りを消せないよう、署名を検証した相手で絞る
     */
    fun removeByNote(
        notePublicId: PublicNoteId,
        actorUri: String,
    ): Boolean

    fun removeByActor(actorUri: String): Int

    /**
     * お気に入りが 1 件も残っていない相手の鍵は返さない。返すと、関わりの切れた
     * 相手の鍵で署名を通せる
     */
    fun findPublicKeyPem(actorUri: String): String?

    /**
     * 投稿ごとのお気に入りの数。1 件も無い投稿は含めない
     */
    fun countsByNotes(notePublicIds: Set<PublicNoteId>): Map<PublicNoteId, Int>

    data class NewNoteFavourite(
        val notePublicId: PublicNoteId,
        val actor: NewRemoteActor,
        val receivedAt: Instant,
    )
}
