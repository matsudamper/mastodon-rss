package net.matsudamper.mastodon.rss.feed

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// RSS 2.0 と RSS 1.0 (RDF) を読めることを確認する。
// 実際の配信元は仕様どおりとは限らないので、拡張要素や余計な入れ子が
// 混ざっていても中身を取り出せることまで見る。
class FeedParserRssTest {
    @Test
    fun `RSS 2_0 のチャンネルと記事を読む`() {
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>技術ブログ</title>
                <link>https://example.com/</link>
                <description>更新情報</description>
                <lastBuildDate>Wed, 02 Oct 2024 08:00:00 +0900</lastBuildDate>
                <item>
                  <title>1 本目の記事</title>
                  <link>https://example.com/1</link>
                  <guid isPermaLink="false">tag:example.com,2024:1</guid>
                  <description>要約</description>
                  <pubDate>Wed, 02 Oct 2024 08:00:00 +0900</pubDate>
                </item>
                <item>
                  <title>2 本目の記事</title>
                  <link>https://example.com/2</link>
                </item>
              </channel>
            </rss>
            """.trimIndent()

        val feed = FeedParser.parse(xml)

        assertEquals(FeedFormat.RSS_2_0, feed.format)
        assertEquals("技術ブログ", feed.title)
        assertEquals("https://example.com/", feed.link)
        assertEquals("更新情報", feed.description?.text)
        assertEquals(Instant.parse("2024-10-01T23:00:00Z"), feed.updatedAt)
        assertEquals(2, feed.items.size)

        val first = feed.items[0]
        assertEquals("1 本目の記事", first.title)
        assertEquals("https://example.com/1", first.link)
        assertEquals("tag:example.com,2024:1", first.id)
        assertEquals("要約", first.summary?.text)
        assertEquals(Instant.parse("2024-10-01T23:00:00Z"), first.publishedAt)

        val second = feed.items[1]
        assertNull(second.id)
        assertNull(second.publishedAt)
    }

    @Test
    fun `image の中の title と link をチャンネルのものと取り違えない`() {
        val xml =
            """
            <rss version="2.0">
              <channel>
                <title>本当の題名</title>
                <link>https://example.com/</link>
                <image>
                  <title>画像の題名</title>
                  <link>https://example.com/image</link>
                  <url>https://example.com/logo.png</url>
                </image>
              </channel>
            </rss>
            """.trimIndent()

        val feed = FeedParser.parse(xml)

        assertEquals("本当の題名", feed.title)
        assertEquals("https://example.com/", feed.link)
    }

    @Test
    fun `atom link が同居していてもチャンネルの link を上書きしない`() {
        val xml =
            """
            <rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
              <channel>
                <title>題名</title>
                <link>https://example.com/</link>
                <atom:link href="https://example.com/feed.xml" rel="self" type="application/rss+xml"/>
              </channel>
            </rss>
            """.trimIndent()

        val feed = FeedParser.parse(xml)

        assertEquals("https://example.com/", feed.link)
    }

    @Test
    fun `content encoded を本文として読む`() {
        val xml =
            """
            <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
              <channel>
                <title>題名</title>
                <item>
                  <title>記事</title>
                  <description>要約だけ</description>
                  <content:encoded><![CDATA[<p>本文の<a href="https://example.com">リンク</a></p>]]></content:encoded>
                </item>
              </channel>
            </rss>
            """.trimIndent()

        val item = FeedParser.parse(xml).items.single()

        assertEquals("要約だけ", item.summary?.text)
        assertEquals("""<p>本文の<a href="https://example.com">リンク</a></p>""", item.content?.text)
        // 本文があるなら本文を優先する
        assertEquals(item.content, item.bodyOrSummary())
    }

    @Test
    fun `知らない拡張要素は読み飛ばす`() {
        val xml =
            """
            <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
              <channel>
                <title>題名</title>
                <item>
                  <media:group>
                    <media:content url="https://example.com/movie.mp4">
                      <media:title>動画の題名</media:title>
                    </media:content>
                  </media:group>
                  <title>記事の題名</title>
                  <link>https://example.com/1</link>
                </item>
              </channel>
            </rss>
            """.trimIndent()

        val item = FeedParser.parse(xml).items.single()

        assertEquals("記事の題名", item.title)
        assertEquals("https://example.com/1", item.link)
    }

    @Test
    fun `題名の前後の空白と改行を落とす`() {
        val xml =
            """
            <rss version="2.0">
              <channel>
                <title>題名</title>
                <item>
                  <title>
                    折り返した
                    記事の題名
                  </title>
                </item>
              </channel>
            </rss>
            """.trimIndent()

        val item = FeedParser.parse(xml).items.single()

        assertEquals("折り返した 記事の題名", item.title)
    }

    @Test
    fun `RSS 1_0 の RDF を読む`() {
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                     xmlns="http://purl.org/rss/1.0/"
                     xmlns:dc="http://purl.org/dc/elements/1.1/">
              <channel rdf:about="https://example.com/feed.rdf">
                <title>RDF のフィード</title>
                <link>https://example.com/</link>
                <description>説明</description>
                <items>
                  <rdf:Seq>
                    <rdf:li rdf:resource="https://example.com/1"/>
                  </rdf:Seq>
                </items>
              </channel>
              <item rdf:about="https://example.com/1">
                <title>RDF の記事</title>
                <link>https://example.com/1</link>
                <description>要約</description>
                <dc:date>2024-10-02T08:00:00+09:00</dc:date>
              </item>
            </rdf:RDF>
            """.trimIndent()

        val feed = FeedParser.parse(xml)

        assertEquals(FeedFormat.RSS_1_0, feed.format)
        assertEquals("RDF のフィード", feed.title)
        assertEquals("https://example.com/", feed.link)

        val item = feed.items.single()
        assertEquals("RDF の記事", item.title)
        // guid が無いので rdf:about を記事の id として使う
        assertEquals("https://example.com/1", item.id)
        assertEquals(Instant.parse("2024-10-01T23:00:00Z"), item.publishedAt)
    }

    @Test
    fun `RDF の items にある rdf li を記事として数えない`() {
        val xml =
            """
            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns="http://purl.org/rss/1.0/">
              <channel>
                <title>題名</title>
                <items>
                  <rdf:Seq>
                    <rdf:li rdf:resource="https://example.com/1"/>
                    <rdf:li rdf:resource="https://example.com/2"/>
                  </rdf:Seq>
                </items>
              </channel>
              <item rdf:about="https://example.com/1"><title>1</title></item>
            </rdf:RDF>
            """.trimIndent()

        assertEquals(1, FeedParser.parse(xml).items.size)
    }

    @Test
    fun `Shift_JIS のバイト列を XML 宣言に従って読む`() {
        val xml =
            """
            <?xml version="1.0" encoding="Shift_JIS"?>
            <rss version="2.0">
              <channel>
                <title>日本語の題名</title>
              </channel>
            </rss>
            """.trimIndent()

        val feed = FeedParser.parse(xml.toByteArray(charset("Shift_JIS")))

        assertEquals("日本語の題名", feed.title)
    }

    @Test
    fun `maxItems を超えた記事は捨てる`() {
        val items = (1..5).joinToString("") { "<item><title>記事$it</title></item>" }
        val xml = """<rss version="2.0"><channel><title>題名</title>$items</channel></rss>"""

        val feed = FeedParser.parse(xml, FeedParserLimits(maxItems = 3))

        assertEquals(3, feed.items.size)
        assertEquals("記事1", feed.items.first().title)
    }

    @Test
    fun `maxTextLength を超えた本文は切り捨てる`() {
        val body = "あ".repeat(100)
        val xml =
            """<rss version="2.0"><channel><title>題名</title>""" +
                """<item><description>$body</description></item></channel></rss>"""

        val feed = FeedParser.parse(xml, FeedParserLimits(maxTextLength = 10))

        val item = feed.items.single()
        val summary = item.summary?.text.orEmpty()
        assertTrue(summary.length <= 10, "切り捨てられていない: ${summary.length} 文字")
    }
}
