package net.matsudamper.mastodon.rss.repository

import java.time.Instant
import net.matsudamper.mastodon.rss.repository.entity.DeliveryId
import net.matsudamper.mastodon.rss.repository.entity.FeedItemId

/**
 * 相手の inbox に送る配信の待ち行列。
 *
 * 1 行が 1 宛先への 1 件。送る中身と署名するアカウントを行が持つので、
 * 取り出した側は行だけ見れば送れる。
 *
 * 投函の口（[enqueueNote]）もここに置く。投稿の記録（`notes`）・記事の投稿済み化
 * （`feed_items`）・投函（`delivery_queue`）は 1 トランザクションで確定させる必要があり、
 * 接続 1 本 + ロックの構成ではリポジトリを跨いで囲えないため。
 *
 * 状態は `pending`（送る時刻を待つ）/ `delivering`（送っている）/ `failed`（諦めた）の 3 つで、
 * 成功した行は消す。送り直しを待つ行は `pending` に戻るので、`failed` は諦めた行だけを指す。
 */
interface DeliveryQueueRepository {
    /**
     * 投稿を記録して、宛先ごとの配信を投函する。
     *
     * [NotePost.feedItemId] があれば、その記事が `pending` のときだけ `posted` にして進む。
     * 既に `posted` なら何も書かずに [EnqueueNoteResult.FeedItemNotPending] を返す。
     * 同じ記事への投函が 2 回走っても、投稿とキューが 1 回しか作られないのはここで止めている。
     *
     * 宛先が 1 つも無くても投稿は記録する。フォロワーがいない間の投稿も `outbox` には並ぶ。
     *
     * 投函した行は投稿に紐付く。投稿を消すと未配信の行も一緒に消えるので、
     * 消したはずの投稿が復旧した相手に後から届くことはない。
     */
    fun enqueueNote(post: NotePost): EnqueueNoteResult

    /**
     * 送る時刻を過ぎた `pending` を、古い順に `delivering` にして返す。
     *
     * 選ぶのと `delivering` にするのを 1 トランザクションで行い、更新できた行だけを返す。
     * 分けると、別のループが同じ行を拾って二重に送る。`attempts` はここで増やす。
     *
     * @param limit 一度に取り出す数。同時に送る数の上限と揃える
     */
    fun claim(
        now: Instant,
        limit: Int,
    ): List<ClaimedDelivery>

    /**
     * 行がまだあるか。
     *
     * claim してから送るまでの間に、投稿が消されて行ごと消えていることがある。
     * まとめて claim した分を順に送る間は開くので、送る直前に確かめる
     */
    fun exists(id: DeliveryId): Boolean

    /**
     * 送れたので行を消す
     */
    fun markDelivered(id: DeliveryId)

    /**
     * 送れなかったので、時刻を指定して `pending` に戻す
     */
    fun scheduleRetry(
        id: DeliveryId,
        nextAttemptAt: Instant,
        error: String,
    )

    /**
     * 諦める。`failed` にして `next_attempt_at` と `body` を NULL にする。
     *
     * 二度と送らないものの本文を残さないため。行そのものは残すので、
     * 死んだインスタンスを 1 つフォローされたままだと `failed` は増え続ける
     */
    fun giveUp(
        id: DeliveryId,
        error: String,
    )

    /**
     * `delivering` のまま残っている行を `pending` に戻す。起動時に呼ぶ。
     *
     * 送信中にプロセスが落ちた分がここに残る。相手に二重に届くことはあるが、
     * ActivityPub の受信側はアクティビティの `id` で冪等に扱うので許容する。
     * 同じ DB に対してプロセスを 2 つ動かすと、動いている方の行まで巻き戻すので、
     * その構成は取らない。
     *
     * @return 戻した件数
     */
    fun recoverDelivering(): Int

    /**
     * そのアカウントが署名する配信の件数。
     *
     * 諦めた行は消えないので、アカウント画面を開くたびにここは増え続ける行を数えることになる。
     * `(username, state, next_attempt_at, id)` のインデックスで、自分の分だけを見て済ませる
     */
    fun counts(username: String): DeliveryQueueCounts

    /**
     * 一度は失敗して送り直しを待っている行（`pending` かつ `attempts > 0`）を、次に送る時刻の順に返す。
     *
     * 位置を件数で数えず、直前のページの最後の 1 件で指す。ワーカーが動いている間は
     * 行が出入りするので、件数で数えると同じ行が 2 回出たり抜けたりする。
     *
     * @param after この位置より後ろを返す。null なら先頭から
     */
    fun listRetrying(
        username: String,
        after: RetryingDeliveryPosition?,
        limit: Int,
    ): List<RetryingDelivery>

    /**
     * 諦めた行を新しい順に返す。
     *
     * @param afterId この id より古いものを返す。null なら先頭から
     */
    fun listFailed(
        username: String,
        afterId: DeliveryId?,
        limit: Int,
    ): List<FailedDelivery>

    /**
     * そのアカウントが署名する配信を全部消す。アカウントを消すときに使う。
     *
     * 残すと、消えたアカウントとして署名しようとして送れない行を延々と送り直す
     *
     * @return 消えた件数
     */
    fun deleteByUsername(username: String): Int
}

/**
 * 投函する投稿。
 *
 * @param body 署名対象になる `Create{Note}` の JSON。宛先ごとに同じものを送る
 * @param inboxes 宛先。同じ宛先は 1 つにまとめてから渡すこと
 * @param enqueuedAt 投函した時刻。最初の 1 回はこの時刻にすぐ送る
 * @param feedItemId 記事から投稿するなら、その記事。管理画面からの告知は null
 */
data class NotePost(
    val note: NewNote,
    val body: String,
    val inboxes: List<String>,
    val enqueuedAt: Instant,
    val feedItemId: FeedItemId?,
)

sealed interface EnqueueNoteResult {
    /**
     * @param deliveries 投函した配信の数。宛先の数と同じ
     */
    data class Queued(
        val deliveries: Int,
    ) : EnqueueNoteResult

    /**
     * 記事が既に投稿済みか消えていたので、何も書いていない
     */
    data object FeedItemNotPending : EnqueueNoteResult
}

/**
 * 何を送る行か。DB には小文字の名前で入る
 */
enum class DeliveryKind {
    /**
     * 投稿を包んだ `Create`
     */
    CREATE_NOTE,
}

/**
 * ワーカーに渡す、`delivering` にした行。
 *
 * @param username 署名するこちらのアカウントの名前
 * @param body 署名対象になる JSON
 * @param attempts この claim を含めた回数。送り直しの間隔を決めるのに使う
 * @param enqueuedAt 投函した時刻。諦める判定に使う
 */
data class ClaimedDelivery(
    val id: DeliveryId,
    val kind: DeliveryKind,
    val username: String,
    val inbox: String,
    val body: String,
    val attempts: Int,
    val enqueuedAt: Instant,
)

/**
 * @param waiting 送る時刻を待っているものと送っている最中のものの合計
 * @param failed 諦めたもの
 */
data class DeliveryQueueCounts(
    val waiting: Long,
    val failed: Long,
)

/**
 * 送り直しを待っている行 1 件
 */
data class RetryingDelivery(
    val id: DeliveryId,
    val inbox: String,
    val attempts: Int,
    val nextAttemptAt: Instant,
    val lastError: String?,
) {
    /**
     * この行を「直前のページの最後」として指す位置
     */
    val position: RetryingDeliveryPosition get() = RetryingDeliveryPosition(nextAttemptAt = nextAttemptAt, id = id)
}

/**
 * 送り直し待ちの一覧の位置。
 *
 * 時刻だけでは同じ時刻の行が並んだときに決まらないので、id まで見て一意にする
 */
data class RetryingDeliveryPosition(
    val nextAttemptAt: Instant,
    val id: DeliveryId,
)

/**
 * 諦めた行 1 件。もう送らないので次に送る時刻は無い
 */
data class FailedDelivery(
    val id: DeliveryId,
    val inbox: String,
    val attempts: Int,
    val lastError: String?,
)
