package net.matsudamper.mastodon.rss.repository

import java.time.Instant

/**
 * 購読しているフィードの読み書き。
 *
 * ここに置く型は保存する形であって、XML を読んだ結果ではない。
 * パーサ側の型（`:backend:rss` の `ParsedFeed`）とは別に定義してある。
 * 同じ型を使い回すと、スキーマを変えるたびにパーサを触ることになる。
 */
interface FeedRepository {
    /** 登録されているフィードを全部返す。管理画面の一覧に使う */
    fun list(): List<Feed>

    fun find(id: FeedId): Feed?

    fun findByAccountId(accountId: AccountId): Feed?

    /** URL で引く。同じ URL の二重登録を弾くために使う */
    fun findByUrl(url: String): Feed?

    /**
     * 次の取得予定が [now] を過ぎているフィードを返す。
     *
     * 定期ポーリングの対象を選ぶためのもの。一度に取りに行く数を [limit] で抑える。
     */
    fun findDue(
        now: Instant,
        limit: Int,
    ): List<Feed>

    /**
     * 登録する。採番した id を含めて返す。同じアカウントか同じ URL が既にあれば null
     */
    fun add(feed: NewFeed): Feed?

    /**
     * フィードから読めた題名とサイトの URL と形式を反映する
     */
    fun updateMetadata(
        id: FeedId,
        title: String?,
        siteUrl: String?,
        format: String?,
    )

    /**
     * 取得に成功したことを記録する。
     *
     * `304 Not Modified` だった場合もここを通す。中身は変わっていないが、
     * 取得自体は成功していて、次の取得予定は進める必要がある。
     */
    fun recordFetchSuccess(
        id: FeedId,
        fetchedAt: Instant,
        validators: FeedFetchValidators,
    )

    /**
     * 取得に失敗したことを記録する。
     *
     * 失敗しても登録は消さない。配信元の一時的な不調と、URL が死んだことの
     * 区別はここでは付けられないので、記録だけして次の取得に任せる。
     */
    fun recordFetchFailure(
        id: FeedId,
        fetchedAt: Instant,
        error: String,
    )

    /**
     * 初回の取り込みが済んだことを記録する。
     *
     * 登録直後の 1 回目は、既にある記事を全部投稿してしまわないよう
     * 「取り込み済み」として記録するだけにする。その判定に使う。
     */
    fun markInitialImportDone(id: FeedId)

    /** 登録を消す。記事も一緒に消える */
    fun delete(id: FeedId)
}

@JvmInline
value class FeedId(
    val value: Long,
)

/**
 * 購読しているフィード 1 本。
 *
 * @param url フィード（RSS/Atom）の URL。取得先
 * @param siteUrl フィードが指している Web サイトの URL。表示用で、取得には使わない
 * @param pollIntervalSeconds 取得の間隔。配信元の更新頻度に合わせて変えられるよう
 *   フィードごとに持つ
 * @param initialImportDone 初回の取り込みが済んでいるか。済んでいなければ投稿しない
 */
data class Feed(
    val id: FeedId,
    val accountId: AccountId,
    val url: String,
    val title: String?,
    val siteUrl: String?,
    val format: String?,
    val pollIntervalSeconds: Long,
    val fetch: FeedFetchStatus,
    val initialImportDone: Boolean,
    val createdAt: Instant,
)

/** 登録するフィード。id と取得状況はまだ無い */
data class NewFeed(
    val accountId: AccountId,
    val url: String,
    val title: String?,
    val siteUrl: String?,
    val format: String?,
    val pollIntervalSeconds: Long,
)

/**
 * 前回の取得の結果。
 *
 * @param lastFetchedAt 成否を問わず、最後に取りに行った時刻。次の取得予定の基準
 * @param lastSucceededAt 最後に成功した時刻。ここが古いままなら配信元が壊れている
 * @param lastError 最後の失敗の内容。成功したら消す
 */
data class FeedFetchStatus(
    val validators: FeedFetchValidators,
    val lastFetchedAt: Instant?,
    val lastSucceededAt: Instant?,
    val lastError: String?,
)

/**
 * 条件付き GET に使う値。
 *
 * 次に取りに行くとき、`ETag` は `If-None-Match` に、`Last-Modified` は
 * `If-Modified-Since` に入れて送る。変わっていなければ配信元は `304` を返すので、
 * 本文を送らせずに済む。
 *
 * どちらも解釈せずに受け取った文字列のまま持つ。`Last-Modified` を時刻として
 * 読み直すと、書式が変わって相手が比較できなくなる。
 */
data class FeedFetchValidators(
    val etag: String?,
    val lastModified: String?,
) {
    companion object {
        /** まだ一度も取得していない状態 */
        val NONE: FeedFetchValidators = FeedFetchValidators(etag = null, lastModified = null)
    }
}
