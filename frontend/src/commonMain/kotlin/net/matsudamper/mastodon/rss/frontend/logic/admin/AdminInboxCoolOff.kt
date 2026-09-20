package net.matsudamper.mastodon.rss.frontend.logic.admin

/**
 * いま inbox に通していない送信元 1 つ。
 *
 * @param until 通すようになる時刻。エポックからの秒数
 * @param rejectedCount 署名を拒否した回数
 * @param blockedRequestCount 検証まで進めずに返したリクエストの数
 */
data class AdminInboxCoolOff(
    val clientIp: String,
    val until: Long,
    val rejectedCount: Int,
    val blockedRequestCount: Int,
)

/**
 * 止め始めた記録 1 件。
 *
 * @param blockedAt 止め始めた時刻。エポックからの秒数
 * @param until そのときに決まった、通すようになる時刻。エポックからの秒数
 */
data class AdminInboxCoolOffRecord(
    val clientIp: String,
    val blockedAt: Long,
    val until: Long,
    val rejectedCount: Int,
)

sealed interface AdminInboxCoolOffsResult {
    data class Success(
        val coolOffs: List<AdminInboxCoolOff>,
        val history: List<AdminInboxCoolOffRecord>,
    ) : AdminInboxCoolOffsResult

    data class Failure(
        val message: String,
    ) : AdminInboxCoolOffsResult
}
