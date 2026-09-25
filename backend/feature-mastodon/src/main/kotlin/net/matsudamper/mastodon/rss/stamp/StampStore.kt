package net.matsudamper.mastodon.rss.stamp

import java.time.Instant
import net.matsudamper.mastodon.rss.actor.RemoteActor
import net.matsudamper.mastodon.rss.entity.PublicNoteId

interface StampStore {
    /**
     * 同じ相手が同じ投稿に押し直したら、前のスタンプを置き換える。
     *
     * @return 記録したら true。投稿が無ければ false
     */
    fun put(stamp: ReceivedStamp): Boolean

    /**
     * @param actorUri 押した相手。他人のスタンプを消されないよう、署名の持ち主で固定する
     */
    fun remove(
        notePublicId: PublicNoteId,
        actorUri: String,
        emoji: String,
    ): Boolean

    fun removeActor(actorUri: String): Int

    fun findPublicKeyPem(actorUri: String): String?

    /**
     * @param emoji 絵文字そのもの、またはカスタム絵文字の `:name:`
     * @param emojiImageUrl カスタム絵文字の画像 URL。Unicode の絵文字では null
     */
    data class ReceivedStamp(
        val notePublicId: PublicNoteId,
        val actor: RemoteActor,
        val emoji: String,
        val emojiImageUrl: String?,
        val receivedAt: Instant,
    )
}
