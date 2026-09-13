package net.matsudamper.mastodon.rss.logic

import net.matsudamper.mastodon.rss.repository.DeliveryQueueCounts
import net.matsudamper.mastodon.rss.repository.DeliveryQueueRepository
import net.matsudamper.mastodon.rss.repository.FailedDelivery
import net.matsudamper.mastodon.rss.repository.RetryingDelivery
import net.matsudamper.mastodon.rss.repository.RetryingDeliveryPosition
import net.matsudamper.mastodon.rss.repository.entity.DeliveryId

/**
 * 管理画面から見た配信キュー。
 *
 * 一覧には必ず件数の上限を付ける。諦めた行は消えないので、上限が無いと
 * 死んだインスタンス 1 つで際限なく返すことになる
 */
class DeliveryQueueService(
    private val deliveryQueue: DeliveryQueueRepository,
) {
    fun counts(username: String): DeliveryQueueCounts = deliveryQueue.counts(username)

    /**
     * @param limit 要求された件数。[MAX_LIST_LIMIT] を超える指定は切り詰める
     */
    fun retrying(
        username: String,
        after: RetryingDeliveryPosition?,
        limit: Int,
    ): RetryingPage {
        val size = limit.coerceIn(0, MAX_LIST_LIMIT)
        if (size == 0) return RetryingPage(deliveries = emptyList(), hasMore = false, nextPosition = null)

        val fetched = deliveryQueue.listRetrying(username = username, after = after, limit = size + 1)
        val page = fetched.take(size)

        return RetryingPage(
            deliveries = page,
            hasMore = fetched.size > size,
            nextPosition = page.lastOrNull()?.position.takeIf { fetched.size > size },
        )
    }

    fun failed(
        username: String,
        afterId: DeliveryId?,
        limit: Int,
    ): FailedPage {
        val size = limit.coerceIn(0, MAX_LIST_LIMIT)
        if (size == 0) return FailedPage(deliveries = emptyList(), hasMore = false, nextId = null)

        val fetched = deliveryQueue.listFailed(username = username, afterId = afterId, limit = size + 1)
        val page = fetched.take(size)

        return FailedPage(
            deliveries = page,
            hasMore = fetched.size > size,
            nextId = page.lastOrNull()?.id.takeIf { fetched.size > size },
        )
    }

    /**
     * @param nextPosition 次のページを取るときに渡す位置。null なら最後のページ
     */
    data class RetryingPage(
        val deliveries: List<RetryingDelivery>,
        val hasMore: Boolean,
        val nextPosition: RetryingDeliveryPosition?,
    )

    data class FailedPage(
        val deliveries: List<FailedDelivery>,
        val hasMore: Boolean,
        val nextId: DeliveryId?,
    )

    companion object {
        /**
         * 1 回で返す件数の上限。画面から指定できる値をそのまま使わない
         */
        const val MAX_LIST_LIMIT: Int = 50
    }
}
