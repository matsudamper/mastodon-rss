package net.matsudamper.mastodon.rss.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FeedHeaderParserTest {
    @Test
    fun `webfeeds coverを優先して読む`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:webfeeds="http://webfeeds.org/rss/1.0">
              <channel>
                <title>sample</title>
                <webfeeds:logo>https://example.com/logo.png</webfeeds:logo>
                <webfeeds:cover image="https://example.com/cover.jpg" />
              </channel>
            </rss>
        """.trimIndent()

        assertEquals("https://example.com/cover.jpg", FeedHeaderParser.parse(xml.encodeToByteArray()))
    }

    @Test
    fun `coverが無ければwebfeeds logoを読む`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom" xmlns:webfeeds="http://webfeeds.org/rss/1.0">
              <title>sample</title>
              <webfeeds:logo>https://example.com/logo.png</webfeeds:logo>
            </feed>
        """.trimIndent()

        assertEquals("https://example.com/logo.png", FeedHeaderParser.parse(xml.encodeToByteArray()))
    }

    @Test
    fun `Atom標準logoはヘッダーにしない`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>sample</title>
              <logo>https://example.com/logo.png</logo>
            </feed>
        """.trimIndent()

        assertNull(FeedHeaderParser.parse(xml.encodeToByteArray()))
    }
}
