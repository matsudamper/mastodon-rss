package net.matsudamper.mastodon.rss.repository

import java.time.Instant

/**
 * 配信した投稿の読み書き。
 *
 * Mastodon は受け取った投稿のパーマリンクを後から引きに来るので、送った中身を残す
 */
interface NoteRepository {
    /**
     * 投稿を記録する。
     *
     * 配信する前に呼ぶ。配信してから記録すると、相手が受け取った直後に
     * パーマリンクを引きに来たときに 404 を返す。
     */
    fun add(note: NewNote)

    fun find(publicId: String): Note?

    /**
     * 新しい順に返す。
     *
     * @param after ここより古いものを返す。null なら先頭から
     */
    fun list(
        username: String,
        after: NoteCursor?,
        limit: Int,
    ): List<Note>

    fun count(username: String): Long
}

/**
 * ページの位置。
 *
 * `publishedAt` だけでは同じ時刻の投稿が並んだときに位置が決まらないので、
 * `publicId` まで見て一意にする
 */
data class NoteCursor(
    val publishedAt: Instant,
    val publicId: String,
)

/**
 * @param publicId URL のパスに入る識別子。採番した id をそのまま出すと、
 *   投稿の総数と作られた順が外から分かってしまう
 */
data class Note(
    val publicId: String,
    val username: String,
    val contentHtml: String,
    val publishedAt: Instant,
)

data class NewNote(
    val username: String,
    val publicId: String,
    val contentHtml: String,
    val publishedAt: Instant,
)
