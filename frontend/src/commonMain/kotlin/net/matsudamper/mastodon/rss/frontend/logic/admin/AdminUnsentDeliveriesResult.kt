package net.matsudamper.mastodon.rss.frontend.logic.admin

/**
 * まだ送り終えていない配信の一覧の問い合わせ結果
 */
sealed interface AdminUnsentDeliveriesResult {
    /**
     * @param nextCursor 続きがあれば、それを取るために渡す印
     */
    data class Success(
        val deliveries: List<AdminUnsentDelivery>,
        val hasMore: Boolean,
        val nextCursor: String?,
    ) : AdminUnsentDeliveriesResult

    data class Failure(
        val message: String,
    ) : AdminUnsentDeliveriesResult
}
