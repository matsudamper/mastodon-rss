package net.matsudamper.mastodon.rss.frontend.logic.admin

/**
 * 相手のホスト 1 つと、そこからのフォローで配信する宛先の数
 */
data class AdminDeliveryTargetHost(
    val host: String,
    val deliveryTargetCount: Int,
)

sealed interface AdminDeliveryTargetLimitResult {
    /**
     * @param maxDeliveryTargetsPerHost 同じホストからのフォローで配信する宛先の上限。超えるフォローは断られる
     * @param hosts 宛先の多い順
     */
    data class Success(
        val maxDeliveryTargetsPerHost: Int,
        val hosts: List<AdminDeliveryTargetHost>,
    ) : AdminDeliveryTargetLimitResult

    data class Failure(
        val message: String,
    ) : AdminDeliveryTargetLimitResult
}

sealed interface AdminSaveMaxDeliveryTargetsPerHostResult {
    data class Success(
        val maxDeliveryTargetsPerHost: Int,
    ) : AdminSaveMaxDeliveryTargetsPerHostResult

    /**
     * 指定できる範囲の外だった
     */
    data class OutOfRange(
        val minValue: Int,
        val maxValue: Int,
    ) : AdminSaveMaxDeliveryTargetsPerHostResult

    data class Failure(
        val message: String,
    ) : AdminSaveMaxDeliveryTargetsPerHostResult
}
