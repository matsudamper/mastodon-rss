package net.matsudamper.mastodon.rss.logic

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
) {
    /**
     * 今置いてあるアイコンの置き場。無ければ null
     */
    fun locate(accountId: AccountId): String? {
        val feed = feeds.findByAccountId(accountId) ?: return null
        return icons.find(feed.id)?.path
    }

    /**
     * アカウント削除前に控える、そのフィード用の画像の置き場。
     *
     * 置き場の名前にフィードの id を含める前に置いたものは列挙で拾えないので、
     * 記録に残っている置き場も足す
     */
    fun locateAll(accountId: AccountId): List<String> {
        val feed = feeds.findByAccountId(accountId) ?: return emptyList()
        val recorded = icons.find(feed.id)?.path
        return (store.paths(feed.id) + listOfNotNull(recorded)).distinct()
    }

    fun delete(path: String) {
        store.delete(path)
    }
}
