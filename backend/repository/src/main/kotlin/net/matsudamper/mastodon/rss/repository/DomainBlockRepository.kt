package net.matsudamper.mastodon.rss.repository

import java.time.Instant

/**
 * 配信・受信を止める相手のドメイン。
 *
 * ドメインは相手の inbox やアクターの URL のホスト名で、小文字に揃えたものを渡すこと。
 *
 * 止めた理由を 2 つに分けて持つ。自動で入れたもの（[DomainBlockReason.UNAVAILABLE]）は
 * 自動で外すが、管理画面から入れたもの（[DomainBlockReason.MANUAL]）は自動では触らない。
 * 手で止めた相手が、配信が 1 回通っただけで元に戻ってはいけない。
 */
interface DomainBlockRepository {
    /**
     * そのドメインへの配信を止めているか
     */
    fun blocksDelivery(domain: String): Boolean

    /**
     * そのドメインからの受信を止めているか
     */
    fun blocksInbox(domain: String): Boolean

    /**
     * 配信を諦めたので、そのドメインへの配信を止める。
     *
     * 既に行があれば何もしない。手で止めているものを自動の理由で上書きすると、
     * 次の配信の成功で外れてしまう。
     *
     * @return 新しく止めたら true
     */
    fun markUnavailable(
        domain: String,
        description: String,
        at: Instant,
    ): Boolean

    /**
     * 自動で止めたものだけを外す。手で止めたものは残す。
     *
     * 配信の成功や署名付きのリクエストの受信のたびに呼ぶので、止めていなければ書き込まずに返る。
     *
     * @return 外したら true
     */
    fun clearUnavailable(domain: String): Boolean

    fun find(domain: String): DomainBlock?

    /**
     * ドメインの順に返す。
     *
     * @param afterDomain このドメインより後ろを返す。null なら先頭から
     */
    fun list(
        afterDomain: String?,
        limit: Int,
    ): List<DomainBlock>

    /**
     * 管理画面から止める。行があれば中身を置き換え、理由を [DomainBlockReason.MANUAL] にする。
     *
     * 自動で止めたものを手で直したら、以後は自動では外さない。
     * 止め始めた時刻は、行があればそのまま残す。
     */
    fun saveManual(
        domain: String,
        blockDelivery: Boolean,
        blockInbox: Boolean,
        description: String?,
        at: Instant,
    ): DomainBlock

    /**
     * 理由を問わず外す
     *
     * @return 外したら true
     */
    fun delete(domain: String): Boolean
}

/**
 * @param domain 小文字に揃えたホスト名
 * @param reasonDescription 止めた理由の説明
 * @param blockDelivery こちらから送らない
 * @param blockInbox 相手から受け取らない
 * @param createdAt 止め始めた時刻
 */
data class DomainBlock(
    val domain: String,
    val reason: DomainBlockReason,
    val reasonDescription: String?,
    val blockDelivery: Boolean,
    val blockInbox: Boolean,
    val createdAt: Instant,
)

enum class DomainBlockReason {
    /**
     * 配信を諦めたので自動で止めた。配信の成功か、そのドメインからの署名付きのリクエストで外れる
     */
    UNAVAILABLE,

    /**
     * 管理画面から止めた。自動では外れない
     */
    MANUAL,
}
