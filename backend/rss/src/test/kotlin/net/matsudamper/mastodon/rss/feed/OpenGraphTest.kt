package net.matsudamper.mastodon.rss.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

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
    fun `script の中の閉じタグで打ち切らない`() {
        val html =
            """
            <head>
            <script>const marker = "</head>";</script>
            <meta property="og:image" content="https://example.com/a.png">
            </head>
            """.trimIndent()

        assertEquals("https://example.com/a.png", OpenGraph.imageUrl(html))
    }

    @Test
    fun `script やコメントの中の meta は読まない`() {
        val html =
            """
            <head>
            <script>document.write('<meta property="og:image" content="https://evil.example/a.png">')</script>
            <!-- <meta property="og:image" content="https://old.example/b.png"> -->
            <meta property="og:image" content="https://example.com/c.png">
            </head>
            """.trimIndent()

        assertEquals("https://example.com/c.png", OpenGraph.imageUrl(html))
    }

    @Test
    fun `og image より og image secure url を優先する`() {
        val html =
            """
            <head>
            <meta property="og:image" content="http://example.com/plain.png">
            <meta property="og:image:secure_url" content="https://example.com/secure.png">
            </head>
            """.trimIndent()

        assertEquals("https://example.com/secure.png", OpenGraph.imageUrl(html))
    }

    @Test
    fun `大文字で書かれたタグと属性も読む`() {
        val html = """<HEAD><META PROPERTY="OG:IMAGE" CONTENT="https://example.com/a.png"></HEAD>"""

        assertEquals("https://example.com/a.png", OpenGraph.imageUrl(html))
    }

    @Test
    fun `body に入ったら読むのをやめる`() {
        val html = """<head><title>題名</title><body><meta property="og:image" content="https://example.com/b.png">"""

        assertNull(OpenGraph.imageUrl(html))
    }

    @Test
    fun `閉じていないコメントやタグが並んでも待たされない`() {
        // 位置ごとに終端を探し直す作りだと、この形で入力長の二乗になる
        val unclosed = "<!--".repeat(200_000) + """<meta property="og:image" content="https://example.com/a.png">"""

        val elapsed = measureTime { assertNull(OpenGraph.imageUrl(unclosed)) }

        assertTrue(elapsed < 5.seconds, "閉じていないコメントの走査に $elapsed かかった")
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
