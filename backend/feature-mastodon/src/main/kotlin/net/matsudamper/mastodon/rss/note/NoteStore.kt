package net.matsudamper.mastodon.rss.note

import java.time.Instant

/**
 * 配信した投稿の置き先。
 *
 * [net.matsudamper.mastodon.rss.follower.FollowerStore] と同じく口だけを決めて、
 * 実装は `:backend` が repository に繋ぐ。
 *
 * 送ったら終わりにはできない。Mastodon は受け取った投稿のパーマリンクを後から
 * 引きに来るので、送った中身をこちらにも残しておく必要がある。
 */
interface NoteStore {
    /**
     * 配信する前に記録する。配信が先だと、直後に引きに来られたときに 404 を返す
     */
    fun add(note: StoredNote)

    fun find(publicId: String): StoredNote?

    /**
     * 新しい順に返す。
     *
     * 位置を件数で数えず、直前のページの最後の 1 件で指す。件数で数えると、
     * 読んでいる間に新しい投稿が入るたびに位置がずれて、同じ投稿が 2 回出たり
     * 抜けたりする。投稿は先頭に増えるので、件数で数えると必ずずれる。
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
 * 並び順の鍵をそのまま持つ。`publishedAt` だけでは同じ時刻の投稿が並んだときに
 * 位置が決まらないので、`publicId` まで見て一意にする。
 *
 * 文字列にして外に出す。中身が読めても困らない（並び順の鍵でしかない）ので、
 * 暗号化も署名もしない。区切りは `_` にしてある。`publicId` は UUID なので混ざらない。
 */
data class NoteCursor(
    val publishedAt: Instant,
    val publicId: String,
) {
    fun encode(): String = "${publishedAt.epochSecond}_${publishedAt.nano}_$publicId"

    companion object {
        /**
         * 読めない形なら null。壊れた cursor は「先頭から」に倒す
         */
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

/**
 * 配信した投稿 1 件。
 *
 * @param publicId URL のパスに入る識別子
 * @param username 投稿したこちらのアカウントの名前
 * @param contentHtml 配信した本文の HTML
 * @param publishedAt 相手に見せる公開日時
 */
data class StoredNote(
    val publicId: String,
    val username: String,
    val contentHtml: String,
    val publishedAt: Instant,
) {
    /**
     * この投稿を「直前のページの最後」として指す位置
     */
    val cursor: NoteCursor get() = NoteCursor(publishedAt = publishedAt, publicId = publicId)
}
