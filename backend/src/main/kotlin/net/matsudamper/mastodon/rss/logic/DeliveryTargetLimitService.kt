package net.matsudamper.mastodon.rss.logic

import net.matsudamper.mastodon.rss.repository.FollowerRepository
import net.matsudamper.mastodon.rss.repository.HostDeliveryTargetCount

/**
 * 同じホストからのフォローで配る先の上限と、いまホストごとにいくつ配る先があるか。
 *
 * 上限で断るのは `RepositoryFollowerStore` で、ここは管理画面に見せるだけ。
 *
 * @param maxDeliveryTargetsPerHost `RepositoryFollowerStore` に渡したものと同じ値を渡す。
 * 違う値だと、画面で上限に届いていないように見えるのに断られる
 */
class DeliveryTargetLimitService(
    val maxDeliveryTargetsPerHost: Int,
    private val followers: FollowerRepository,
) {
    /**
     * 配る先の多いホストから返す。
     *
     * @param limit 要求された件数。[MAX_HOSTS_LIMIT] を超える指定は切り詰める
     */
    fun hosts(limit: Int): List<HostDeliveryTargetCount> =
        followers.deliveryTargetCountsByHost(limit = limit.coerceIn(0, MAX_HOSTS_LIMIT))

    companion object {
        /**
         * 1 回で返すホストの数の上限。画面から指定できる値をそのまま使わない
         */
        const val MAX_HOSTS_LIMIT: Int = 100
    }
}
