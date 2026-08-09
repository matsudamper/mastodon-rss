package net.matsudamper.mastodon.rss.feed

import java.security.MessageDigest

/**
 * 記事を区別するための鍵。
 *
 * 取り込みのたびにフィード全体が返ってくるので、「どれが新着か」は
 * 前回までに見たものと突き合わせて決める。その突き合わせに使う値。
 *
 * 同じ記事にはいつも同じ鍵が出ること（さもないと同じ記事を何度も投稿する）と、
 * 別の記事が同じ鍵にならないこと（さもないと新着を取りこぼす）の両方が要る。
 * 取りこぼしより二重投稿の方が目立つので、迷ったら変わりにくい方を採る。
 *
 * フィードをまたいだ一意性は考えない。保存するときにフィードの id と組で持つ前提。
 *
 * @param value 突き合わせに使う文字列
 * @param source どこから作った値か。運用でどの経路に落ちているかを見るために持つ
 */
data class FeedItemKey(
    val value: String,
    val source: Source,
) {
    enum class Source {
        /** `guid` / Atom の `id` / RSS 1.0 の `rdf:about` */
        ID,

        /** 記事の URL */
        LINK,

        /** 上のどちらも無い。フィードの URL と記事の内容から作ったハッシュ */
        HASH,
    }

    companion object {
        /**
         * 記事から鍵を作る。
         *
         * 優先順は `id` → `link` → ハッシュ。
         *
         * `id` を最優先にするのは、記事の URL が後から変わる（配信元がパスの付け方を
         * 変える、パラメータが付く）ことがあるため。`id` は変えないことになっている。
         *
         * どちらも無い場合はフィードの URL と題名からハッシュを作る。題名まで
         * 無いときは本文も混ぜる。この経路では、配信元が本文を直すと別の記事に
         * 見えて二重に投稿されるが、他に手掛かりが無い。
         *
         * @param feedUrl フィードの URL。ハッシュにだけ使う
         */
        fun of(
            feedUrl: String,
            item: ParsedFeedItem,
        ): FeedItemKey {
            item.id?.trim()?.takeIf { it.isNotEmpty() }?.let {
                return FeedItemKey(value = it, source = Source.ID)
            }
            item.link?.trim()?.takeIf { it.isNotEmpty() }?.let {
                return FeedItemKey(value = it, source = Source.LINK)
            }

            val title = item.title?.trim().orEmpty()
            // 題名まで無いときだけ本文を混ぜる。本文は配信元が直すことがあるので、
            // 使わずに済むなら使わない
            val body = if (title.isEmpty()) item.bodyOrSummary()?.toPlainText().orEmpty() else ""

            return FeedItemKey(
                value = sha256Hex("$feedUrl\n$title\n$body"),
                source = Source.HASH,
            )
        }

        private fun sha256Hex(value: String): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }
    }
}
