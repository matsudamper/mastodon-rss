package net.matsudamper.mastodon.rss.feed

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Atom 1.0 を読めることを確認する。
// RSS と違って link が属性で、本文の種類が type 属性で決まるので、そこを重点的に見る。
class FeedParserAtomTest {
    @Test
    fun `フィードとエントリを読む`() {
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Atom のフィード</title>
              <subtitle>説明</subtitle>
              <link rel="self" href="https://example.com/feed.atom"/>
              <link rel="alternate" type="text/html" href="https://example.com/"/>
              <updated>2024-10-02T08:00:00+09:00</updated>
              <entry>
                <title>記事の題名</title>
                <link rel="alternate" type="text/html" href="https://example.com/1"/>
                <id>tag:example.com,2024:1</id>
                <summary>要約</summary>
                <published>2024-10-01T00:00:00Z</published>
                <updated>2024-10-02T00:00:00Z</updated>
              </entry>
            </feed>
            """.trimIndent()

        val feed = FeedParser.parse(xml)

        assertEquals(FeedFormat.ATOM_1_0, feed.format)
        assertEquals("Atom のフィード", feed.title)
        // self ではなく alternate を採る
        assertEquals("https://example.com/", feed.link)
        assertEquals(Instant.parse("2024-10-01T23:00:00Z"), feed.updatedAt)

        val entry = feed.items.single()
        assertEquals("記事の題名", entry.title)
        assertEquals("https://example.com/1", entry.link)
        assertEquals("tag:example.com,2024:1", entry.id)
        assertEquals("要約", entry.summary?.text)
        assertEquals(FeedContent.Type.TEXT, entry.summary?.type)
        assertEquals(Instant.parse("2024-10-01T00:00:00Z"), entry.publishedAt)
        assertEquals(Instant.parse("2024-10-02T00:00:00Z"), entry.updatedAt)
    }

    @Test
    fun `published が無ければ updated を公開日時にする`() {
        val xml =
            """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>題名</title>
              <entry>
                <title>記事</title>
                <updated>2024-10-02T00:00:00Z</updated>
              </entry>
            </feed>
            """.trimIndent()

        val entry = FeedParser.parse(xml).items.single()

        assertEquals(Instant.parse("2024-10-02T00:00:00Z"), entry.publishedAt)
    }

    @Test
    fun `type が html の本文は HTML として扱う`() {
        val xml =
            """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>題名</title>
              <entry>
                <content type="html">&lt;p&gt;本文&lt;/p&gt;</content>
              </entry>
            </feed>
            """.trimIndent()

        val item = FeedParser.parse(xml).items.single()
        val content = item.content

        assertEquals(FeedContent.Type.HTML, content?.type)
        assertEquals("<p>本文</p>", content?.text)
    }

    @Test
    fun `type が xhtml の本文はタグごと取り出す`() {
        val xml =
            """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>題名</title>
              <entry>
                <content type="xhtml">
                  <div xmlns="http://www.w3.org/1999/xhtml">
                    <p>本文の<a href="https://example.com/1">リンク</a></p>
                  </div>
                </content>
              </entry>
            </feed>
            """.trimIndent()

        val item = FeedParser.parse(xml).items.single()
        val content = item.content

        assertEquals(FeedContent.Type.HTML, content?.type)
        val contentText = content?.text.orEmpty()
        assertTrue(contentText.contains("<p>"), "段落のタグが残っていない: $contentText")
        assertTrue(contentText.contains("""<a href="https://example.com/1">リンク</a>"""), "リンクが残っていない: $contentText")
    }

    @Test
    fun `題名に HTML が入っていてもプレーンテキストにする`() {
        val xml =
            """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title type="html">&lt;b&gt;太字の&lt;/b&gt;題名</title>
              <entry>
                <title type="html">記事 &amp;amp; 続き</title>
              </entry>
            </feed>
            """.trimIndent()

        val feed = FeedParser.parse(xml)

        assertEquals("太字の題名", feed.title)
        assertEquals("記事 & 続き", feed.items.single().title)
    }

    @Test
    fun `alternate が無ければ最初のリンクを使わない`() {
        val xml =
            """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>題名</title>
              <entry>
                <title>記事</title>
                <link rel="enclosure" type="audio/mpeg" href="https://example.com/1.mp3"/>
              </entry>
            </feed>
            """.trimIndent()

        // 添付ファイルを記事の URL にすると、記事ではないものへ誘導することになる
        val item = FeedParser.parse(xml).items.single()

        assertNull(item.link)
    }

    @Test
    fun `maxItems を超えたエントリは捨てる`() {
        val entries = (1..5).joinToString("") { "<entry><title>記事$it</title></entry>" }
        val xml = """<feed xmlns="http://www.w3.org/2005/Atom"><title>題名</title>$entries</feed>"""

        val feed = FeedParser.parse(xml, FeedParserLimits(maxItems = 2))

        assertEquals(2, feed.items.size)
        assertEquals("記事1", feed.items.first().title)
    }

    @Test
    fun `rel が無い link は alternate として扱う`() {
        val xml =
            """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>題名</title>
              <entry>
                <title>記事</title>
                <link href="https://example.com/1"/>
              </entry>
            </feed>
            """.trimIndent()

        val item = FeedParser.parse(xml).items.single()

        assertEquals("https://example.com/1", item.link)
    }
}
