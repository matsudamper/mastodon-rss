package net.matsudamper.mastodon.rss.logic

import java.net.IDN
import java.net.URI
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import net.matsudamper.mastodon.rss.repository.DomainBlock
import net.matsudamper.mastodon.rss.repository.DomainBlockRepository
import org.slf4j.LoggerFactory

/**
 * 配信・受信を止める相手のドメイン。
 *
 * 配信を諦めたドメインへは送らないようにして、相手が戻ってきたら送り始める。
 * 落ちた相手にも新しい投稿のたびに行が積まれ、それぞれが諦めるまで送り直しを繰り返すのを止める。
 * Mastodon もドメインごとに「利用不可」を持ち、配信の成功か、そのドメインのアクターからの
 * 署名付きリクエストで外す。
 *
 * 管理画面から止めたものは、自動では外さない。
 */
class DomainBlockService(
    private val domainBlocks: DomainBlockRepository,
    private val clock: () -> Instant,
) {
    /**
     * ドメインごとの、最後に動いていると分かった時刻。
     *
     * 同じドメインへの配信は並べて送るので、動いていると分かった後で、期限間近の古い行の失敗が
     * 返ってくることがある。その失敗でドメインを止めると、動いている相手への配信が止まる。
     * 状態はメモリに持つ。再起動で消えても、次に動いていると分かったときにまた記録される
     */
    private val lastAvailableAt = ConcurrentHashMap<String, Instant>()

    /**
     * 動いていると分かった記録と止めるかの判断を、ドメインの記録と合わせて 1 つずつ行う。
     * 分けると、判断した後に動いていると分かった記録が割り込み、その後から止まる
     */
    private val availabilityLock = Any()

    /**
     * その inbox への配信を止めているか。読めない URL は止めない
     */
    fun blocksDeliveryTo(inbox: String): Boolean {
        val domain = domainOf(inbox) ?: return false
        return domainBlocks.blocksDelivery(domain)
    }

    /**
     * その URL の持ち主からの受信を止めているか。読めない URL は止めない
     *
     * @param url 相手のアクターや鍵の URL
     */
    fun blocksInboxFrom(url: String): Boolean {
        val domain = domainOf(url) ?: return false
        return domainBlocks.blocksInbox(domain)
    }

    /**
     * 配信を諦めたので、その inbox のドメインへの配信を止める。既に止めていれば何もしない。
     *
     * 諦めた配信を投函した後に、そのドメインが動いていると分かっていれば止めない
     *
     * @param reason 諦めたときの失敗の理由
     * @param enqueuedAt 諦めた配信を投函した時刻
     */
    fun markUnavailable(
        inbox: String,
        reason: String,
        enqueuedAt: Instant,
    ) {
        val domain = domainOf(inbox) ?: return
        synchronized(availabilityLock) {
            val availableAt = lastAvailableAt[domain]
            if (availableAt != null && !availableAt.isBefore(enqueuedAt)) {
                logger.info("諦めた配信の後に動いていると分かっているので $domain は止めない: $reason")
                return
            }
            if (domainBlocks.markUnavailable(domain = domain, description = reason, at = clock())) {
                logger.warn("配信を諦めたので $domain への配信を止める: $reason")
            }
        }
    }

    /**
     * その URL のドメインが動いていると分かったので、自動で止めていた配信を再開する
     *
     * @param url 送れた inbox や、署名付きのリクエストを送ってきたアクターの URL
     */
    fun markAvailable(url: String) {
        val domain = domainOf(url) ?: return
        synchronized(availabilityLock) {
            lastAvailableAt[domain] = clock()
            if (domainBlocks.clearUnavailable(domain)) {
                logger.info("$domain が戻ってきたので配信を再開する")
            }
        }
    }

    /**
     * ドメインの順に返す。
     *
     * @param limit 要求された件数。[MAX_LIST_LIMIT] を超える指定は切り詰める
     */
    fun list(
        afterDomain: String?,
        limit: Int,
    ): Page {
        val size = limit.coerceIn(0, MAX_LIST_LIMIT)
        if (size == 0) return Page(blocks = listOf(), hasMore = false, nextDomain = null)

        val fetched = domainBlocks.list(afterDomain = afterDomain, limit = size + 1)
        val page = fetched.take(size)
        val hasMore = fetched.size > size

        return Page(
            blocks = page,
            hasMore = hasMore,
            nextDomain = page.lastOrNull()?.domain.takeIf { hasMore },
        )
    }

    /**
     * 管理画面から止める。行があれば置き換える。
     *
     * 自動で止めたものを保存し直すと手動に変わり、以後は自動では外れない。
     *
     * 何も止めない行は作らない。手動の行があると、届かなくなっても自動で止められない。
     * 止めるのをやめるなら [delete] で外す
     *
     * @param domain 入力されたドメイン。URL を貼られたときはホスト名を取り出す
     */
    fun save(
        domain: String,
        blockDelivery: Boolean,
        blockInbox: Boolean,
        description: String,
    ): SaveResult {
        val normalized = normalizeInput(domain) ?: return SaveResult.InvalidDomain
        if (!blockDelivery && !blockInbox) return SaveResult.NothingBlocked
        val trimmedDescription = description.trim()
        if (trimmedDescription.codePointCount(0, trimmedDescription.length) > DESCRIPTION_MAX_LENGTH) {
            return SaveResult.DescriptionTooLong(DESCRIPTION_MAX_LENGTH)
        }

        val saved = domainBlocks.saveManual(
            domain = normalized,
            blockDelivery = blockDelivery,
            blockInbox = blockInbox,
            description = trimmedDescription.ifEmpty { null },
            at = clock(),
        )
        logger.info("管理画面から $normalized を止めた: 配信=$blockDelivery 受信=$blockInbox")
        return SaveResult.Success(saved)
    }

    /**
     * 理由を問わず外す
     *
     * @return 外したドメイン。止めていなかったら null
     */
    fun delete(domain: String): String? {
        val normalized = normalizeInput(domain) ?: return null
        if (!domainBlocks.delete(normalized)) return null

        logger.info("管理画面から $normalized を外した")
        return normalized
    }

    /**
     * URL のホスト名を、ドメインとして比べられる形にする。
     *
     * ホスト名は大文字小文字を区別しないので揃える
     */
    private fun domainOf(url: String): String? =
        runCatching { URI(url).host }.getOrNull()?.lowercase(Locale.ROOT)?.ifEmpty { null }

    /**
     * 管理画面の入力をドメインにする。ドメインとして読めなければ null。
     *
     * 国際化ドメインは Punycode にする。相手の URL に入っているのはそちらの形
     */
    private fun normalizeInput(input: String): String? {
        val trimmed = input.trim()
        val host = if (trimmed.contains("://")) domainOf(trimmed) else trimmed
        if (host.isNullOrEmpty()) return null

        val ascii = runCatching { IDN.toASCII(host, IDN.ALLOW_UNASSIGNED) }.getOrNull() ?: return null
        val lowercase = ascii.lowercase(Locale.ROOT)
        if (!DOMAIN_PATTERN.matches(lowercase)) return null

        return lowercase
    }

    /**
     * @param nextDomain 次のページを取るときに渡す位置。null なら最後のページ
     */
    data class Page(
        val blocks: List<DomainBlock>,
        val hasMore: Boolean,
        val nextDomain: String?,
    )

    sealed interface SaveResult {
        data class Success(
            val block: DomainBlock,
        ) : SaveResult

        data object InvalidDomain : SaveResult

        /**
         * 配信も受信も止めない指定だった
         */
        data object NothingBlocked : SaveResult

        data class DescriptionTooLong(
            val maxLength: Int,
        ) : SaveResult
    }

    companion object {
        /**
         * 1 回で返す件数の上限。画面から指定できる値をそのまま使わない
         */
        const val MAX_LIST_LIMIT: Int = 50

        const val DESCRIPTION_MAX_LENGTH: Int = 500

        private val DOMAIN_PATTERN = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*$")

        private val logger = LoggerFactory.getLogger(DomainBlockService::class.java)
    }
}
