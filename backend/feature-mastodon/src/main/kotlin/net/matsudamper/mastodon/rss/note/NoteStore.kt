package net.matsudamper.mastodon.rss.note

import java.time.Instant

/**
 * 配信した投稿の置き先。
 *
 * Mastodon は受け取った投稿のパーマリンクを後から引きに来るので、
 * 送った中身をこちらにも残す。
 */
interface NoteStore {
    fun add(note: StoredNote)

    fun find(publicId: String): StoredNote?

    /**
     * 新しい順に返す。
     *
     * @param after ここより古いものを返す。null なら先頭から
     */
    fun list(
        username: String,
        after: NoteCursor?,
        limit: Int,
    ): List<StoredNote>

    fun count(username: String): Long
}

/**
 * ページの位置。
 *
 * `publishedAt` だけでは同じ時刻の投稿が並んだときに位置が決まらないので、
 * `publicId` まで見て一意にする。
 */
data class NoteCursor(
    val publishedAt: Instant,
    val publicId: String,
) {
    fun encode(): String = "${publishedAt.epochSecond}_${publishedAt.nano}_$publicId"

    companion object {
        fun decode(raw: String): NoteCursor? {
            val parts = raw.split('_', limit = 3)
            if (parts.size != 3) return null

            val epochSecond = parts[0].toLongOrNull() ?: return null
            val nano = parts[1].toLongOrNull() ?: return null
            if (parts[2].isEmpty()) return null

            return runCatching {
                NoteCursor(
                    publishedAt = Instant.ofEpochSecond(epochSecond, nano),
                    publicId = parts[2],
                )
            }.getOrNull()
        }
    }
}

data class StoredNote(
    val publicId: String,
    val username: String,
    val contentHtml: String,
    val publishedAt: Instant,
) {
    val cursor: NoteCursor get() = NoteCursor(publishedAt = publishedAt, publicId = publicId)
}
