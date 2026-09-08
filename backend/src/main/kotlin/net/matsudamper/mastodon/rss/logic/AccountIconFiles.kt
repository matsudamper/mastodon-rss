package net.matsudamper.mastodon.rss.logic

import net.matsudamper.mastodon.rss.repository.FeedHeaderRepository
import net.matsudamper.mastodon.rss.repository.FeedIconRepository
import net.matsudamper.mastodon.rss.repository.FeedRepository
import net.matsudamper.mastodon.rss.shared.AccountId

/**
 * アカウントに紐づいて置いてあるプロフィール画像のファイル。
 *
 * アカウントを消すと、フィードと画像の行は外部キーで一緒に消えるが、
 * ファイルは残る。行が消えた後は置き場を引けなくなるので、消す前に控えておく。
 */
class AccountIconFiles(
    private val feeds: FeedRepository,
    private val icons: FeedIconRepository,
    private val store: FeedIconStore,
    private val headers: FeedHeaderRepository? = null,
) {
    /**
     * 今置いてあるアイコンの置き場。無ければ null
     */
    fun locate(accountId: AccountId): String? {
        val feed = feeds.findByAccountId(accountId) ?: return null
        return icons.find(feed.id)?.path
    }

    /**
     * アカウント削除前に控える、アイコンとヘッダーの置き場。
     */
    fun locateAll(accountId: AccountId): List<String> {
        val feed = feeds.findByAccountId(accountId) ?: return emptyList()
        return listOfNotNull(
            icons.find(feed.id)?.path,
            headers?.find(feed.id)?.path,
        ).distinct()
    }

    fun delete(path: String) {
        store.delete(path)
    }
}
