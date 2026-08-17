package net.matsudamper.mastodon.rss.logic

import net.matsudamper.mastodon.rss.actor.ActorDirectory
import net.matsudamper.mastodon.rss.note.NoteCursor
import net.matsudamper.mastodon.rss.note.NotePublisher
import net.matsudamper.mastodon.rss.note.NoteStore
import net.matsudamper.mastodon.rss.note.PublishedNote
import net.matsudamper.mastodon.rss.note.StoredNote

/**
 * 管理画面から見た投稿の操作。
 *
 * 本文はプレーンテキストで受け、配信する HTML に組み立てるのはここ。
 * HTML をそのまま受けると、管理画面を通して任意のタグをフォロワーに配ることになる。
 */
class NoteService(
    private val directory: ActorDirectory,
    private val publisher: NotePublisher,
    private val notes: NoteStore,
) {
    suspend fun post(
        username: String,
        body: String,
    ): PostResult {
        val urls = directory.resolve(username)
            ?: return PostResult.Failure(unknownAccount = true, isEmpty = false, tooLong = false)

        val text = body.trim()
        if (text.isEmpty()) {
            return PostResult.Failure(unknownAccount = false, isEmpty = true, tooLong = false)
        }
        // 書いた人にとっての文字数と合わせるため、コードポイントで数える
        if (text.codePointCount(0, text.length) > MAX_LENGTH) {
            return PostResult.Failure(unknownAccount = false, isEmpty = false, tooLong = true)
        }

        return PostResult.Success(publisher.publish(sender = urls, contentHtml = toHtml(text)))
    }

    /**
     * 新しい順に返す。名前が引き当てられなければ空。
     *
     * @param cursor 直前のページの最後を指す文字列。null と読めない値は先頭から
     * @param limit 要求された件数。[MAX_LIST_LIMIT] を超える指定は切り詰める
     */
    fun notes(
        username: String,
        cursor: String?,
        limit: Int?,
    ): NotePage {
        val urls = directory.resolve(username) ?: return NotePage(notes = emptyList(), cursor = null)

        val size = (limit ?: DEFAULT_LIST_LIMIT).coerceIn(1, MAX_LIST_LIMIT)
        val page = notes.list(
            username = urls.username,
            after = cursor?.let { NoteCursor.decode(it) },
            limit = size,
        )

        return NotePage(
            notes = page,
            cursor = if (page.size < size) null else page.last().cursor.encode(),
        )
    }

    /**
     * @param cursor 次のページを取るときに渡す値。null なら最後のページ
     */
    data class NotePage(
        val notes: List<StoredNote>,
        val cursor: String?,
    )

    sealed interface PostResult {
        data class Success(
            val published: PublishedNote,
        ) : PostResult

        /**
         * 通らなかった理由。当てはまらないものは false にして並べて返す
         */
        data class Failure(
            val unknownAccount: Boolean,
            val isEmpty: Boolean,
            val tooLong: Boolean,
        ) : PostResult
    }

    companion object {
        /**
         * 本文の長さの上限。
         *
         * Mastodon の既定の投稿長は 500 文字で、超えた分は相手側で切られる。
         * ここで弾いておけば、切られたものが配られてから気付く形にならない。
         */
        const val MAX_LENGTH: Int = 500

        const val DEFAULT_LIST_LIMIT: Int = 20

        /**
         * 1 回で返す件数の上限。画面から指定できる値をそのまま使わない
         */
        const val MAX_LIST_LIMIT: Int = 50

        /**
         * プレーンテキストを配信する HTML に直す。
         *
         * 空行で段落に分け、行の切れ目は `<br>` にする。Mastodon が許可するのは
         * この程度のタグで、それ以外は相手側で落とされる。
         */
        internal fun toHtml(text: String): String = text
            .replace("\r\n", "\n")
            .split(Regex("\n{2,}"))
            .filter { it.isNotBlank() }
            .joinToString("") { paragraph ->
                val escaped = paragraph.trim().split("\n").joinToString("<br>") { escapeHtml(it) }
                "<p>$escaped</p>"
            }

        private fun escapeHtml(raw: String): String = raw
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
