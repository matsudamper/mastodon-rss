package net.matsudamper.mastodon.rss.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OpenGraphTest {
    @Test
    fun `og image を読む`() {
        val html =
            """
            <html><head>
            <meta property="og:image" content="https://example.com/image.png">
            </head><body></body></html>
            """.trimIndent()

        assertEquals("https://example.com/image.png", OpenGraph.imageUrl(html))
    }

    @Test
    fun `property ではなく name で書かれていても読む`() {
        val html = """<head><meta name="og:image" content="https://example.com/a.png"></head>"""

        assertEquals("https://example.com/a.png", OpenGraph.imageUrl(html))
    }

    @Test
    fun `属性の順番が入れ替わっていても読む`() {
        val html = """<head><meta content="https://example.com/a.png" property="og:image"/></head>"""

        assertEquals("https://example.com/a.png", OpenGraph.imageUrl(html))
    }

    @Test
    fun `シングルクォートと引用符なしの属性を読む`() {
        val single = """<head><meta property='og:image' content='https://example.com/a.png'></head>"""
        val bare = """<head><meta property=og:image content=https://example.com/b.png></head>"""

        assertEquals("https://example.com/a.png", OpenGraph.imageUrl(single))
        assertEquals("https://example.com/b.png", OpenGraph.imageUrl(bare))
    }

    @Test
    fun `og image が無ければ og image url を読む`() {
        val html = """<head><meta property="og:image:url" content="https://example.com/a.png"></head>"""

        assertEquals("https://example.com/a.png", OpenGraph.imageUrl(html))
    }

    @Test
    fun `og image url より og image を優先する`() {
        val html =
            """
            <head>
            <meta property="og:image:url" content="https://example.com/url.png">
            <meta property="og:image" content="https://example.com/image.png">
            </head>
            """.trimIndent()

        assertEquals("https://example.com/image.png", OpenGraph.imageUrl(html))
    }

    @Test
    fun `複数枚あれば最初の 1 枚を返す`() {
        val html =
            """
            <head>
            <meta property="og:image" content="https://example.com/1.png">
            <meta property="og:image" content="https://example.com/2.png">
            </head>
            """.trimIndent()

        assertEquals("https://example.com/1.png", OpenGraph.imageUrl(html))
    }

    @Test
    fun `実体参照を戻す`() {
        val html = """<head><meta property="og:image" content="https://example.com/a.png?w=1&amp;h=2"></head>"""

        assertEquals("https://example.com/a.png?w=1&h=2", OpenGraph.imageUrl(html))
    }

    @Test
    fun `数値参照を戻す`() {
        val html = """<head><meta property="og:image" content="https://example.com/a.png?q=&#x26;&#38;"></head>"""

        assertEquals("https://example.com/a.png?q=&&", OpenGraph.imageUrl(html))
    }

    @Test
    fun `相対 URL はそのまま返す`() {
        val html = """<head><meta property="og:image" content="/images/a.png"></head>"""

        assertEquals("/images/a.png", OpenGraph.imageUrl(html))
    }

    @Test
    fun `head の外の og image は読まない`() {
        val html =
            """
            <head><title>題名</title></head>
            <body><meta property="og:image" content="https://example.com/body.png"></body>
            """.trimIndent()

        assertNull(OpenGraph.imageUrl(html))
    }

    @Test
    fun `og image が無ければ null`() {
        val html = """<head><meta name="description" content="説明"><meta property="og:title" content="題名"></head>"""

        assertNull(OpenGraph.imageUrl(html))
    }

    @Test
    fun `content が空なら null`() {
        val html = """<head><meta property="og:image" content="  "></head>"""

        assertNull(OpenGraph.imageUrl(html))
    }

    @Test
    fun `HTML でない文字列を渡しても落ちない`() {
        assertNull(OpenGraph.imageUrl(""))
        assertNull(OpenGraph.imageUrl("<<<"))
    }
}
