package net.matsudamper.mastodon.rss.logic

import net.matsudamper.mastodon.rss.actor.ActorDirectory
import net.matsudamper.mastodon.rss.entity.PublicNoteId as MastodonPublicNoteId
import net.matsudamper.mastodon.rss.note.NotePosition
import net.matsudamper.mastodon.rss.note.NoteStore
import net.matsudamper.mastodon.rss.note.StoredNote
import net.matsudamper.mastodon.rss.shared.PublicNoteId

/**
 * 記録済みの投稿を読む。
 *
 * 画面から来る名前を [ActorDirectory] で引き当ててから読むので、
 * 他のアカウントの投稿は id を知っていても返らない。
 */
class NoteReader(
    private val directory: ActorDirectory,
    private val notes: NoteStore,
) {
    /**
     * 新しい順に返す。名前が引き当てられなければ空。
     *
     * @param after 直前のページの最後の位置。null なら先頭から
     * @param limit 要求された件数。[MAX_LIST_LIMIT] を超える指定は切り詰める
     */
    fun notes(
        username: String,
        after: NotePosition?,
        limit: Int,
    ): NotePage {
        val urls = directory.resolve(username)
            ?: return NotePage(notes = emptyList(), hasMore = false, nextPosition = null)

        val size = limit.coerceIn(0, MAX_LIST_LIMIT)
        if (size == 0) return NotePage(notes = emptyList(), hasMore = false, nextPosition = null)

        val fetched = notes.list(username = urls.username, after = after, limit = size + 1)
        val page = fetched.take(size)

        return NotePage(
            notes = page,
            hasMore = fetched.size > size,
            nextPosition = page.lastOrNull()?.position.takeIf { fetched.size > size },
        )
    }

    /**
     * 公開 id だけを新しい順に返す。本文は取らない
     */
    fun noteIds(
        username: String,
        after: NotePosition?,
        limit: Int,
    ): PublicNoteIdPage {
        val urls = directory.resolve(username)
            ?: return PublicNoteIdPage(ids = emptyList(), hasMore = false, nextPosition = null)

        val size = limit.coerceIn(0, MAX_LIST_LIMIT)
        if (size == 0) return PublicNoteIdPage(ids = emptyList(), hasMore = false, nextPosition = null)

        val fetched = notes.listPositions(username = urls.username, after = after, limit = size + 1)
        val page = fetched.take(size)

        return PublicNoteIdPage(
            ids = page.map { PublicNoteId(it.publicId.value) },
            hasMore = fetched.size > size,
            nextPosition = page.lastOrNull().takeIf { fetched.size > size },
        )
    }

    /**
     * アカウントを問わず、公開 id だけを新しい順に返す。トップのタイムラインに使う。
     *
     * 名前を引き当てる相手がいないので [ActorDirectory] は通さない。消したアカウントの
     * 投稿は一緒に消えているので、持ち主を確かめることもしない
     */
    fun timelineNoteIds(
        after: NotePosition?,
        limit: Int,
    ): PublicNoteIdPage {
        val size = limit.coerceIn(0, MAX_LIST_LIMIT)
        if (size == 0) return PublicNoteIdPage(ids = emptyList(), hasMore = false, nextPosition = null)

        val fetched = notes.listAllPositions(after = after, limit = size + 1)
        val page = fetched.take(size)

        return PublicNoteIdPage(
            ids = page.map { PublicNoteId(it.publicId.value) },
            hasMore = fetched.size > size,
            nextPosition = page.lastOrNull().takeIf { fetched.size > size },
        )
    }

    fun note(
        username: String,
        publicId: PublicNoteId,
    ): StoredNote? {
        val urls = directory.resolve(username) ?: return null
        return notes.find(MastodonPublicNoteId(publicId.value))?.takeIf { it.username == urls.username }
    }

    fun noteCounts(usernames: Set<String>): Map<String, Long> {
        if (usernames.isEmpty()) return emptyMap()

        val resolved = directory.resolve(usernames)
        val counts = notes.counts(resolved.values.map { it.username }.toSet())

        return usernames.associateWith { username ->
            val urls = resolved[username] ?: return@associateWith 0L
            counts[urls.username] ?: 0L
        }
    }

    /**
     * @param nextPosition 次のページを取るときに渡す位置。null なら最後のページ
     */
    data class NotePage(
        val notes: List<StoredNote>,
        val hasMore: Boolean,
        val nextPosition: NotePosition?,
    )

    data class PublicNoteIdPage(
        val ids: List<PublicNoteId>,
        val hasMore: Boolean,
        val nextPosition: NotePosition?,
    )

    companion object {
        /**
         * 1 回で返す件数の上限。画面から指定できる値をそのまま使わない
         */
        const val MAX_LIST_LIMIT: Int = 50
    }
}
