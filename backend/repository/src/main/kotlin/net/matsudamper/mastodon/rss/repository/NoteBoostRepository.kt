package net.matsudamper.mastodon.rss.repository

import java.time.Instant
import net.matsudamper.mastodon.rss.shared.PublicNoteId

/**
 * こちらの投稿が受けたブースト
 */
interface NoteBoostRepository {
    /**
     * 同じ相手が同じ投稿をブーストし直しても行を増やさない。
     *
     * @return 記録したら true。既にあるか、投稿が無ければ false
     */
    fun add(boost: NewNoteBoost): Boolean

    /**
     * @param actorUri ブーストした相手。他人のブーストを消せないよう、署名を検証した相手で絞る
     * @param activityUri 取り消す `Announce` の id
     */
    fun removeByActivity(
        actorUri: String,
        activityUri: String,
    ): Boolean

    fun removeByActor(actorUri: String): Int

    /**
     * ブーストが 1 件も残っていない相手の鍵は返さない。返すと、関わりの切れた
     * 相手の鍵で署名を通せる
     */
    fun findPublicKeyPem(actorUri: String): String?

    /**
     * 投稿ごとのブーストの数。1 件も無い投稿は含めない
     */
    fun countsByNotes(notePublicIds: Set<PublicNoteId>): Map<PublicNoteId, Int>

    data class NewNoteBoost(
        val notePublicId: PublicNoteId,
        val actor: NewRemoteActor,
        val activityUri: String,
        val receivedAt: Instant,
    )
}
