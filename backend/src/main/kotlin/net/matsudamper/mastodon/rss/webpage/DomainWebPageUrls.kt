package net.matsudamper.mastodon.rss.webpage

import net.matsudamper.activitypub.entity.PublicNoteId
import net.matsudamper.activitypub.url.WebPageUrls
import net.matsudamper.mastodon.rss.shared.WebPagePath

/**
 * 画面の URL。パスは画面側と同じ [WebPagePath]、ホスト名は `DOMAIN`。
 *
 * scheme が常に `https` なのはアクターの URL と同じ理由。相手はここを人が開く
 * リンクとして扱うので、平文で出すと踏んだ側が平文で繋ぎに行くことになる。
 */
class DomainWebPageUrls(
    private val domain: String,
) : WebPageUrls {
    override fun profile(username: String): String = "https://$domain${WebPagePath.account(username)}"

    override fun note(
        username: String,
        publicId: PublicNoteId,
    ): String = "https://$domain${WebPagePath.accountNote(username = username, noteId = publicId.value)}"
}
