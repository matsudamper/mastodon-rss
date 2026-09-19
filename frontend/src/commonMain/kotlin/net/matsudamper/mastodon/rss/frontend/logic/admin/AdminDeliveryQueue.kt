package net.matsudamper.mastodon.rss.frontend.logic.admin

import net.matsudamper.mastodon.rss.frontend.graphql.type.AdminDeliveryKind as GraphQlAdminDeliveryKind

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
 * @param kind 何を送る配信か
 * @param nextAttemptAt 次に送る時刻。エポックからの秒数
 */
data class AdminRetryingDelivery(
    val kind: AdminDeliveryKind,
    val inbox: String,
    val attempts: Int,
    val nextAttemptAt: Long,
    val lastError: String?,
)

/**
 * @param kind 何を送る配信か
 */
data class AdminFailedDelivery(
    val kind: AdminDeliveryKind,
    val inbox: String,
    val attempts: Int,
    val lastError: String?,
)

/**
 * 配信が何を送るものか。
 *
 * サーバーが増やした種別を知らない版でも画面が出せるよう、知らない値は [UNKNOWN] にする
 */
enum class AdminDeliveryKind {
    CREATE_NOTE,
    DELETE_NOTE,
    ACCEPT_FOLLOW,
    UPDATE_ACTOR,
    DELETE_ACTOR,
    UNKNOWN,
}

/**
 * サーバーの種別を画面側の型に持ち替える。
 *
 * 知らない値はまとめて [AdminDeliveryKind.UNKNOWN] にする。増えた種別で画面が落ちない
 */
internal fun GraphQlAdminDeliveryKind.toAdminDeliveryKind(): AdminDeliveryKind =
    when (this) {
        GraphQlAdminDeliveryKind.CREATE_NOTE -> AdminDeliveryKind.CREATE_NOTE
        GraphQlAdminDeliveryKind.DELETE_NOTE -> AdminDeliveryKind.DELETE_NOTE
        GraphQlAdminDeliveryKind.ACCEPT_FOLLOW -> AdminDeliveryKind.ACCEPT_FOLLOW
        GraphQlAdminDeliveryKind.UPDATE_ACTOR -> AdminDeliveryKind.UPDATE_ACTOR
        GraphQlAdminDeliveryKind.DELETE_ACTOR -> AdminDeliveryKind.DELETE_ACTOR
        GraphQlAdminDeliveryKind.UNKNOWN__ -> AdminDeliveryKind.UNKNOWN
    }

/**
 * 一度は送れず、送り直しを待っている配信。アカウントを問わない一覧に出す。
 *
 * @param sending いま送っている最中か
 * @param nextAttemptAt 次に送る時刻。エポックからの秒数
 */
data class AdminAccountRetryingDelivery(
    val kind: AdminDeliveryKind,
    val username: String,
    val inbox: String,
    val attempts: Int,
    val nextAttemptAt: Long,
    val sending: Boolean,
    val lastError: String?,
)
