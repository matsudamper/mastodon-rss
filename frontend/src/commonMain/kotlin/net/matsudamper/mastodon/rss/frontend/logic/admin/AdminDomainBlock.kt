package net.matsudamper.mastodon.rss.frontend.logic.admin

import net.matsudamper.mastodon.rss.frontend.graphql.type.AdminDomainBlockReason as GraphQlAdminDomainBlockReason

/**
 * 配信・受信を止めている相手のドメイン
 *
 * @param blockDelivery こちらから送らない
 * @param blockInbox 相手から受け取らない
 * @param createdAt 止め始めた時刻。エポックからの秒数
 */
data class AdminDomainBlock(
    val domain: String,
    val reason: AdminDomainBlockReason,
    val reasonDescription: String?,
    val blockDelivery: Boolean,
    val blockInbox: Boolean,
    val createdAt: Long,
)

/**
 * ドメインを止めた理由。
 *
 * サーバーが増やした理由を知らない版でも画面が出せるよう、知らない値は [UNKNOWN] にする
 */
enum class AdminDomainBlockReason {
    /**
     * 届かなくなったので自動で止めた。相手が戻ってきたら自動で外れる
     */
    UNAVAILABLE,

    /**
     * 管理画面から止めた
     */
    MANUAL,

    UNKNOWN,
}

internal fun GraphQlAdminDomainBlockReason.toAdminDomainBlockReason(): AdminDomainBlockReason =
    when (this) {
        GraphQlAdminDomainBlockReason.UNAVAILABLE -> AdminDomainBlockReason.UNAVAILABLE
        GraphQlAdminDomainBlockReason.MANUAL -> AdminDomainBlockReason.MANUAL
        GraphQlAdminDomainBlockReason.UNKNOWN__ -> AdminDomainBlockReason.UNKNOWN
    }

sealed interface AdminDomainBlocksResult {
    /**
     * @param nextCursor 続きがあれば、それを取るために渡す印
     */
    data class Success(
        val blocks: List<AdminDomainBlock>,
        val hasMore: Boolean,
        val nextCursor: String?,
    ) : AdminDomainBlocksResult

    data class Failure(
        val message: String,
    ) : AdminDomainBlocksResult
}

sealed interface AdminSaveDomainBlockResult {
    data class Success(
        val block: AdminDomainBlock,
    ) : AdminSaveDomainBlockResult

    /**
     * @param invalidDomain ドメインとして読めない
     * @param nothingBlocked 配信も受信も止めない指定だった
     * @param reasonDescriptionMaxLength 理由の説明が長すぎるときの上限
     */
    data class Rejected(
        val invalidDomain: Boolean,
        val nothingBlocked: Boolean,
        val reasonDescriptionMaxLength: Int?,
    ) : AdminSaveDomainBlockResult

    data class Failure(
        val message: String,
    ) : AdminSaveDomainBlockResult
}

sealed interface AdminDeleteDomainBlockResult {
    data object Success : AdminDeleteDomainBlockResult

    /**
     * 止めていなかった。別の画面から先に外されていた
     */
    data object NotFound : AdminDeleteDomainBlockResult

    data class Failure(
        val message: String,
    ) : AdminDeleteDomainBlockResult
}
