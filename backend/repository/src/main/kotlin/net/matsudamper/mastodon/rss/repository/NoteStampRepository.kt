package net.matsudamper.mastodon.rss.repository

import java.time.Instant
import net.matsudamper.mastodon.rss.shared.PublicNoteId

interface NoteStampRepository {
    /**
     * 同じ相手が同じ投稿に押し直したら、前のスタンプを置き換える。
     *
     * @return 記録したら true。投稿が無ければ false
     */
    fun put(stamp: NewNoteStamp): Boolean

    /**
     * @param actorUri 押した相手。他人のスタンプを消せないよう、署名を検証した相手で絞る
     * @param emoji 押し替えた後に古いスタンプの取り消しが届いても、今のスタンプを消さないよう絵文字でも絞る
     */
    fun remove(
        notePublicId: PublicNoteId,
        actorUri: String,
        emoji: String,
    ): Boolean

    fun removeByActor(actorUri: String): Int

    /**
     * スタンプが 1 件も残っていない相手の鍵は返さない。返すと、関わりの切れた
     * 相手の鍵で署名を通せる
     */
    fun findPublicKeyPem(actorUri: String): String?

    /**
     * 投稿ごとに、絵文字とその数を多い順に返す。1 件も無い投稿は含めない
     */
    fun countsByNotes(notePublicIds: Set<PublicNoteId>): Map<PublicNoteId, List<StampCount>>

    data class NewNoteStamp(
        val notePublicId: PublicNoteId,
        val actor: NewRemoteActor,
        val emoji: String,
        val emojiImageUrl: String?,
        val receivedAt: Instant,
    )

    /**
     * @param emojiImageUrl 同じ名前でも配信元のサーバーごとに画像が違う。どれか 1 つを返す
     */
    data class StampCount(
        val emoji: String,
        val emojiImageUrl: String?,
        val count: Int,
    )
}
