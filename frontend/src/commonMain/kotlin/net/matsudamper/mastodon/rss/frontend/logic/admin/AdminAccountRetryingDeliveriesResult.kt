package net.matsudamper.mastodon.rss.frontend.logic.admin

/**
 * 送り直しを待っている配信の一覧の問い合わせ結果
 */
sealed interface AdminAccountRetryingDeliveriesResult {
    /**
     * @param nextCursor 続きがあれば、それを取るために渡す印
     */
    data class Success(
        val deliveries: List<AdminAccountRetryingDelivery>,
        val hasMore: Boolean,
        val nextCursor: String?,
    ) : AdminAccountRetryingDeliveriesResult

    data class Failure(
        val message: String,
    ) : AdminAccountRetryingDeliveriesResult
}
