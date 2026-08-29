package net.matsudamper.mastodon.rss.repository

import java.time.Instant

/**
 * 取り込んだ記事の読み書き。
 *
 * この repository が担うのは差分検出と投稿状態の管理の 2 つ。
 *
 * 差分検出は、取り込みのたびにフィード全体が返ってくることへの対処。
 * 記事ごとの鍵（`:backend:rss` の `FeedItemKey`）を保存しておき、
 * 次の取得で見た鍵のうち、まだ無いものだけを新着として扱う。
 *
 * 投稿状態を持つのは、取り込みと配信を分けるため。取り込みの時点で
 * 配信まで済ませようとすると、相手のサーバーが遅いだけで取り込みが止まる。
 */
interface FeedItemRepository {
    /**
     * [keys] のうち、既に保存されているものを返す。
     *
     * 新着の判定に使う。1 件ずつ問い合わせると記事の数だけ往復するので、
     * まとめて渡してまとめて受け取る形にする。
     */
    fun findExistingKeys(
        feedId: FeedId,
        keys: Collection<String>,
    ): Set<String>

    /**
     * 記事を保存する。
     *
     * 同じ鍵が既にある場合は保存せず null を返す。取得と保存の間に別の経路で
     * 保存されることがあるので、[findExistingKeys] で確かめた後でも
     * ここで一意制約に頼る。二重投稿はここで止める。
     */
    fun add(item: NewFeedItem): FeedItem?

    /**
     * まだ投稿していない記事を、古い順に返す。
     *
     * @param limit 一度に取り出す数。溜まっているときに全部読み込まないため
     */
    fun findPending(limit: Int): List<FeedItem>

    /**
     * 指定したフィードのうち、まだ投稿していない記事を古い順に返す。
     */
    fun findPending(
        feedId: FeedId,
        limit: Int,
    ): List<FeedItem>

    /**
     * 投稿し終わったことを記録する。
     *
     * @param noteId 配信した投稿の `notes.public_id`。記事と投稿はこれだけで紐づく
     */
    fun markPosted(
        id: FeedItemId,
        postedAt: Instant,
        noteId: String,
    )

    /**
     * 投稿の対象にしないことを記録する。
     *
     * 題名もリンクも無く本文を組み立てられない場合に使う。
     */
    fun markSkipped(id: FeedItemId)

    /**
     * 投稿の `notes.public_id` から、その投稿の元になった記事を引く。
     *
     * 投稿の一覧に記事を並べるのに使う。1 件ずつ問い合わせると投稿の数だけ
     * 往復するので、まとめて渡してまとめて受け取る形にする
     */
    fun findByNoteIds(noteIds: Collection<String>): Map<String, FeedItem>

    /**
     * 引き当てられなければ null
     */
    fun find(id: FeedItemId): FeedItem?

    /**
     * 記事をまとめて消す。消せた件数を返す。
     *
     * 消すと次の取得で新着として戻ってくる。投稿済みのものを消して
     * 投稿し直すのに使う。配信した投稿（`notes`）はここでは消さない。
     */
    fun delete(ids: Collection<FeedItemId>): Int

    /** フィードの記事を数える。初回の取り込みかどうかの判定などに使う */
    fun countByFeed(feedId: FeedId): Long
}

@JvmInline
value class FeedItemId(
    val value: Long,
)

/**
 * 取り込んだ記事 1 件。
 *
 * @param itemKey 記事を区別する鍵。`:backend:rss` の `FeedItemKey.value`。
 *   フィードの中で一意。フィードをまたぐと重なりうるので、一意制約は
 *   `(feed_id, item_key)` で張る
 * @param contentHtml 投稿する本文。サニタイズ済みの HTML を取り込みの時点で作って持つ。
 *   配信に失敗して後から送り直すときに、配信元から取り直さずに済ませるため
 * @param publishedAt 配信元が名乗っている公開日時。順序付けに使う。
 *   信用しきれない（未来の日時や、更新のたびに現在時刻になるものがある）
 * @param noteId 投稿したときに配信した `notes.public_id`。未投稿なら null
 */
data class FeedItem(
    val id: FeedItemId,
    val feedId: FeedId,
    val itemKey: String,
    val title: String?,
    val link: String?,
    val contentHtml: String?,
    val publishedAt: Instant?,
    val importedAt: Instant,
    val state: FeedItemState,
    val postedAt: Instant?,
    val noteId: String?,
)

/** 保存する記事。id はまだ無い */
data class NewFeedItem(
    val feedId: FeedId,
    val itemKey: String,
    val title: String?,
    val link: String?,
    val contentHtml: String?,
    val publishedAt: Instant?,
    val importedAt: Instant,
    val state: FeedItemState,
)

enum class FeedItemState {
    /** 投稿待ち */
    PENDING,

    /** 投稿済み */
    POSTED,

    /**
     * 投稿しない。
     *
     * 題名もリンクも無く本文を組み立てられない場合。
     * 消さずに残すのは、消すと次の取得で新着として戻ってくるため
     */
    SKIPPED,
}
