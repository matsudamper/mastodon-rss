package net.matsudamper.mastodon.rss.repository

import java.time.Instant

/**
 * 配信した投稿の読み書き。
 *
 * 送ったら終わりにはできない。Mastodon は受け取った投稿のパーマリンクを後から
 * 引きに来るので、送った中身をこちらにも残しておく必要がある。
 * `outbox` を返すのにも要る。
 */
interface NoteRepository {
    /**
     * 投稿を記録する。
     *
     * 配信する前に呼ぶ。配信してから記録すると、相手が受け取った直後に
     * パーマリンクを引きに来たときに 404 を返すことになる。
     */
    fun add(note: NewNote)

    /**
     * 公開 id で引く。`GET /notes/{publicId}` に使う
     */
    fun find(publicId: String): Note?

    fun findByPublicIds(publicIds: Set<String>): Map<String, Note>

    /**
     * 消す。消えていれば何もしない。
     *
     * 消した投稿を元にした記事（`feed_items`）は残り、`note_id` だけが外れる
     */
    fun delete(publicId: String)

    /**
     * 新しい順に返す。`outbox` と管理画面の一覧に使う。
     *
     * 位置を件数で数えず、直前のページの最後の 1 件で指す。件数で数えると、
     * 読んでいる間に新しい投稿が入るたびに位置がずれて、同じ投稿が 2 回出たり
     * 抜けたりする。投稿は先頭に増えるので、offset では必ずずれる。
     *
     * @param after ここより古いものを返す。null なら先頭から
     */
    fun list(
        username: String,
        after: NotePosition?,
        limit: Int,
    ): List<Note>

    /**
     * 新しい順に公開 id だけ返す。本文は取らない
     */
    fun listPositions(
        username: String,
        after: NotePosition?,
        limit: Int,
    ): List<NotePosition>

    fun count(username: String): Long
}

/**
 * ページの位置。
 *
 * 並び順の鍵をそのまま持つ。`publishedAt` だけでは同じ時刻の投稿が並んだときに
 * 位置が決まらないので、`publicId` まで見て一意にする。
 */
data class NotePosition(
    val publishedAt: Instant,
    val publicId: String,
)

/**
 * 配信した投稿 1 件。
 *
 * @param publicId URL のパスに入る識別子。採番した id をそのまま出すと、
 *   投稿の総数と作られた順が外から分かってしまう
 * @param username 投稿したこちらのアカウントの名前
 * @param contentHtml 配信した本文の HTML。サニタイズ済みのものが入っている
 * @param publishedAt 相手に見せる公開日時
 */
data class Note(
    val publicId: String,
    val username: String,
    val contentHtml: String,
    val publishedAt: Instant,
)

/**
 * 記録する投稿
 */
data class NewNote(
    val username: String,
    val publicId: String,
    val contentHtml: String,
    val publishedAt: Instant,
)
