package net.matsudamper.mastodon.rss.repository.sqlite

import java.net.URI

/**
 * inbox の URL から、同じ相手かどうかを見るための鍵を作る。
 */
internal object InboxHost {
    /**
     * 読めない URL は URL 全体を鍵にする。同じ壊れた宛先同士だけが同じ鍵になる
     */
    fun of(inbox: String): String = runCatching { URI(inbox).host }.getOrNull() ?: inbox
}
