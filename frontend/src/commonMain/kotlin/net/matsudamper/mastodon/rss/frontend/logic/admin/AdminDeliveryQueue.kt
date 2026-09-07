package net.matsudamper.mastodon.rss.frontend.logic.admin

/**
 * アカウントが署名する配信キューの状態。
 *
 * @param waitingCount 送る時刻を待っているものと送っている最中のものの合計
 * @param failedCount 諦めたもの
 * @param retrying 送り直しを待っている配信。先頭のページだけ
 * @param retryingHasMore [retrying] に載せ切れなかった分があるか
 * @param failed 諦めた配信。先頭のページだけ
 * @param failedHasMore [failed] に載せ切れなかった分があるか
 */
data class AdminDeliveryQueue(
    val waitingCount: Int,
    val failedCount: Int,
    val retrying: List<AdminRetryingDelivery>,
    val retryingHasMore: Boolean,
    val failed: List<AdminFailedDelivery>,
    val failedHasMore: Boolean,
)

/**
 * @param nextAttemptAt 次に送る時刻。エポックからの秒数
 */
data class AdminRetryingDelivery(
    val inbox: String,
    val attempts: Int,
    val nextAttemptAt: Long,
    val lastError: String?,
)

data class AdminFailedDelivery(
    val inbox: String,
    val attempts: Int,
    val lastError: String?,
)
