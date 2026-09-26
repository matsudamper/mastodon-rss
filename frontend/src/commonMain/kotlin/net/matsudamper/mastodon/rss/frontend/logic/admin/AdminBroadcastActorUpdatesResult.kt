package net.matsudamper.mastodon.rss.frontend.logic.admin

sealed interface AdminBroadcastActorUpdatesResult {
    /**
     * @param accountCount 配信を積めたアカウントの数
     * @param deliveryCount 積んだ配信の数
     * @param failedAccountCount 配信を積めなかったアカウントの数
     */
    data class Success(
        val accountCount: Int,
        val deliveryCount: Int,
        val failedAccountCount: Int,
    ) : AdminBroadcastActorUpdatesResult

    data class Failure(
        val message: String,
    ) : AdminBroadcastActorUpdatesResult
}
