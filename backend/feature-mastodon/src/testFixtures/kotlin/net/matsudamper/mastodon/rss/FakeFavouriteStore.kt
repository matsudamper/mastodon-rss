package net.matsudamper.mastodon.rss

import net.matsudamper.mastodon.rss.entity.PublicNoteId
import net.matsudamper.mastodon.rss.favourite.FavouriteStore
import net.matsudamper.mastodon.rss.favourite.ReceivedFavourite

/**
 * お気に入りの記録の差し替え。オンメモリで持つ。
 *
 * SQL の振る舞いは `:backend:repository` のテストが本物の SQLite で確かめる。
 * こちらが受け持つのは、inbox のハンドラが何を記録して何を消したかの確認。
 */
class FakeFavouriteStore : FavouriteStore {
    val rows: MutableList<ReceivedFavourite> = mutableListOf()

    override fun add(favourite: ReceivedFavourite): Boolean {
        // 一意制約と同じ判定。アクティビティの id は相手ごとに見る
        val duplicated = rows.any {
            it.actor.actorId == favourite.actor.actorId &&
                (it.activityUri == favourite.activityUri || it.notePublicId == favourite.notePublicId)
        }
        if (duplicated) return false

        rows += favourite
        return true
    }

    override fun removeByActivityUri(
        actorUri: String,
        activityUri: String,
    ): Boolean = rows.removeAll { it.actor.actorId == actorUri && it.activityUri == activityUri }

    override fun removeByNote(
        notePublicId: PublicNoteId,
        actorUri: String,
    ): Boolean = rows.removeAll { it.notePublicId == notePublicId && it.actor.actorId == actorUri }

    override fun removeActor(actorUri: String): Int {
        val before = rows.size
        rows.removeAll { it.actor.actorId == actorUri }
        return before - rows.size
    }

    override fun findPublicKeyPem(actorUri: String): String? =
        rows.firstOrNull { it.actor.actorId == actorUri }?.actor?.publicKeyPem
}
