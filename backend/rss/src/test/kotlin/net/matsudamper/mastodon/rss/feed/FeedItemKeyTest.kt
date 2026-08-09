package net.matsudamper.mastodon.rss.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

// 記事を区別する鍵を確認する。
// 同じ記事にいつも同じ鍵が出ること（二重投稿しない）と、
// 別の記事が同じ鍵にならないこと（新着を取りこぼさない）の両方を見る。
class FeedItemKeyTest {
    private val feedUrl = "https://example.com/feed.xml"

    @Test
    fun `id があれば id を使う`() {
        val key = FeedItemKey.of(feedUrl, item(id = "tag:example.com,2024:1", link = "https://example.com/1"))

        assertEquals("tag:example.com,2024:1", key.value)
        assertEquals(FeedItemKey.Source.ID, key.source)
    }

    @Test
    fun `id が無ければ link を使う`() {
        val key = FeedItemKey.of(feedUrl, item(link = "https://example.com/1"))

        assertEquals("https://example.com/1", key.value)
        assertEquals(FeedItemKey.Source.LINK, key.source)
    }

    @Test
    fun `id も link も無ければ URL と題名のハッシュを使う`() {
        val key = FeedItemKey.of(feedUrl, item(title = "記事の題名"))

        assertEquals(FeedItemKey.Source.HASH, key.source)
        assertEquals(64, key.value.length, "SHA-256 の 16 進表記になっていない: ${key.value}")
        // 同じ入力からは同じ鍵が出る
        assertEquals(key, FeedItemKey.of(feedUrl, item(title = "記事の題名")))
    }

    @Test
    fun `題名が違えばハッシュも違う`() {
        val first = FeedItemKey.of(feedUrl, item(title = "1 本目"))
        val second = FeedItemKey.of(feedUrl, item(title = "2 本目"))

        assertNotEquals(first, second)
    }

    @Test
    fun `フィードが違えばハッシュも違う`() {
        val first = FeedItemKey.of("https://example.com/a.xml", item(title = "題名"))
        val second = FeedItemKey.of("https://example.com/b.xml", item(title = "題名"))

        assertNotEquals(first, second)
    }

    @Test
    fun `題名も無ければ本文をハッシュに混ぜる`() {
        val first = FeedItemKey.of(feedUrl, item(summary = FeedContent("本文 1", FeedContent.Type.TEXT)))
        val second = FeedItemKey.of(feedUrl, item(summary = FeedContent("本文 2", FeedContent.Type.TEXT)))

        assertEquals(FeedItemKey.Source.HASH, first.source)
        assertNotEquals(first, second)
    }

    @Test
    fun `空白だけの id は無いものとして扱う`() {
        val key = FeedItemKey.of(feedUrl, item(id = "   ", link = "https://example.com/1"))

        assertEquals(FeedItemKey.Source.LINK, key.source)
    }

    @Test
    fun `id の前後の空白は落とす`() {
        val padded = FeedItemKey.of(feedUrl, item(id = "  https://example.com/1  "))
        val plain = FeedItemKey.of(feedUrl, item(id = "https://example.com/1"))

        assertEquals(plain, padded)
    }

    private fun item(
        id: String? = null,
        title: String? = null,
        link: String? = null,
        summary: FeedContent? = null,
    ): ParsedFeedItem =
        ParsedFeedItem(
            id = id,
            title = title,
            link = link,
            summary = summary,
            content = null,
            publishedAt = null,
            updatedAt = null,
        )
}
