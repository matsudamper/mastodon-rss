package net.matsudamper.mastodon.rss.actor

/**
 * アクターが配信しているフィードの URL の引き先。
 *
 * どこに保存されているかは Actor 側の関心ではないので、
 * [StoredActorNames] と同じく名前を渡して引けることだけを決めておく。
 */
interface StoredFeedLinks {
    /**
     * 名前で引く。フィードを持たないアカウントは [FeedLinks.EMPTY]。
     *
     * 渡すのは [StoredActorNames] が返した保存側の綴り。
     */
    fun find(username: String): FeedLinks
}

/**
 * プロフィールに載せるフィードの URL。
 *
 * @param siteUrl フィードが指している Web サイトの URL
 * @param feedUrl 取得元の RSS/Atom の URL
 */
data class FeedLinks(
    val siteUrl: String?,
    val feedUrl: String?,
) {
    companion object {
        val EMPTY: FeedLinks = FeedLinks(siteUrl = null, feedUrl = null)
    }
}
