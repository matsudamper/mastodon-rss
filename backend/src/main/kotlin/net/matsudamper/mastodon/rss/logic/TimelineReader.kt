package net.matsudamper.mastodon.rss.logic

import net.matsudamper.mastodon.rss.repository.NotePosition
import net.matsudamper.mastodon.rss.repository.NoteRepository
import net.matsudamper.mastodon.rss.shared.PublicNoteId

/**
 * アカウントを問わず、配信した投稿を新しい順に読む。トップのタイムラインに使う。
 *
 * [NoteReader] と違って名前を引き当てる必要が無いので、repository を直接読む。
 * 消したアカウントの投稿は一緒に消えているので、ここで持ち主を確かめることはしない
 */
class TimelineReader(
    private val notes: NoteRepository,
) {
    /**
     * @param after 直前のページの最後の位置。null なら先頭から
     * @param limit 要求された件数。[NoteReader.MAX_LIST_LIMIT] を超える指定は切り詰める
     */
    fun noteIds(
        after: NotePosition?,
        limit: Int,
    ): Page {
        val size = limit.coerceIn(0, NoteReader.MAX_LIST_LIMIT)
        if (size == 0) return Page(ids = emptyList(), hasMore = false, nextPosition = null)

        val fetched = notes.listAllPositions(after = after, limit = size + 1)
        val page = fetched.take(size)

        return Page(
            ids = page.map { PublicNoteId(it.publicId.value) },
            hasMore = fetched.size > size,
            nextPosition = page.lastOrNull().takeIf { fetched.size > size },
        )
    }

    /**
     * @param nextPosition 次のページを取るときに渡す位置。null なら最後のページ
     */
    data class Page(
        val ids: List<PublicNoteId>,
        val hasMore: Boolean,
        val nextPosition: NotePosition?,
    )
}
