package net.matsudamper.mastodon.rss.logic

import net.matsudamper.mastodon.rss.repository.FollowerRepository
import net.matsudamper.mastodon.rss.repository.HostDeliveryTargetCount
import net.matsudamper.mastodon.rss.repository.ServerSettingRepository
import org.slf4j.LoggerFactory

/**
 * 同じホストからのフォローで配る先の上限と、いまホストごとにいくつ配る先があるか。
 *
 * 上限で断るのは `RepositoryFollowerStore` で、ここは値を決めて管理画面に見せるだけ。
 * 普通のサーバーが上限に当たったときに、管理画面から上げられるよう DB に持つ。
 */
class DeliveryTargetLimitService(
    private val settings: ServerSettingRepository,
    private val followers: FollowerRepository,
) {
    /**
     * いま効いている上限。`Follow` を受けるたびに読み直すので、保存した値はすぐ効く
     */
    fun maxDeliveryTargetsPerHost(): Int = settings.maxDeliveryTargetsPerHost() ?: DEFAULT_MAX_DELIVERY_TARGETS_PER_HOST

    /**
     * 上限を下げても、既に受け付けたフォローは消さない。次に受ける `Follow` から断る
     */
    fun save(value: Int): SaveResult {
        if (value !in MIN_MAX_DELIVERY_TARGETS_PER_HOST..MAX_MAX_DELIVERY_TARGETS_PER_HOST) {
            return SaveResult.OutOfRange(
                minValue = MIN_MAX_DELIVERY_TARGETS_PER_HOST,
                maxValue = MAX_MAX_DELIVERY_TARGETS_PER_HOST,
            )
        }

        settings.saveMaxDeliveryTargetsPerHost(value)
        logger.info("管理画面から同じホストの配る先の上限を $value にした")
        return SaveResult.Success(value)
    }

    /**
     * 配る先の多いホストから返す。
     *
     * @param limit 要求された件数。[MAX_HOSTS_LIMIT] を超える指定は切り詰める
     */
    fun hosts(limit: Int): List<HostDeliveryTargetCount> =
        followers.deliveryTargetCountsByHost(limit = limit.coerceIn(0, MAX_HOSTS_LIMIT))

    sealed interface SaveResult {
        data class Success(
            val maxDeliveryTargetsPerHost: Int,
        ) : SaveResult

        data class OutOfRange(
            val minValue: Int,
            val maxValue: Int,
        ) : SaveResult
    }

    companion object {
        /**
         * 1 回で返すホストの数の上限。画面から指定できる値をそのまま使わない
         */
        const val MAX_HOSTS_LIMIT: Int = 100

        /**
         * Mastodon などの普通のサーバーは `sharedInbox` で 1 か所にまとまるので、
         * 効いてくるのは `sharedInbox` を持たない実装だけ
         */
        private const val DEFAULT_MAX_DELIVERY_TARGETS_PER_HOST = 20

        private const val MIN_MAX_DELIVERY_TARGETS_PER_HOST = 1

        /**
         * 上げすぎると、偽アクターを並べて投稿 1 件ごとの配信を増やすのを止められなくなる
         */
        private const val MAX_MAX_DELIVERY_TARGETS_PER_HOST = 1000

        private val logger = LoggerFactory.getLogger(DeliveryTargetLimitService::class.java)
    }
}
