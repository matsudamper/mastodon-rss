package net.matsudamper.mastodon.rss.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FaviconResolverTest {
    @Test
    fun `rel icon の相対 URL をページ基準で解決する`() {
        val html =
            """
            <html><head>
              <link href="../assets/favicon.png?x=1&amp;y=2" rel="icon">
            </head></html>
            """.trimIndent()

        assertEquals(
            "https://example.com/blog/assets/favicon.png?x=1&y=2",
            FaviconResolver.resolve("https://example.com/blog/posts/", html),
        )
    }

    @Test
    fun `shortcut icon も favicon として扱う`() {
        val html = """<link REL='shortcut icon' HREF='/favicon.webp'>"""

        assertEquals(
            "https://example.com/favicon.webp",
            FaviconResolver.resolve("https://example.com/page", html),
        )
    }

    @Test
    fun `SVG は飛ばして次の icon を使う`() {
        val html =
            """
            <link rel="icon" type="image/svg+xml" href="/favicon.svg">
            <link rel="icon" type="image/png" href="/favicon.png">
            """.trimIndent()

        assertEquals(
            "https://example.com/favicon.png",
            FaviconResolver.resolve("https://example.com/", html),
        )
    }

    @Test
    fun `icon 宣言が無ければ origin の favicon ico`() {
        assertEquals(
            "https://example.com:8443/favicon.ico",
            FaviconResolver.resolve("https://example.com:8443/blog/page", "<html></html>"),
        )
    }

    @Test
    fun `HTTP 以外のページ URL は扱わない`() {
        assertNull(FaviconResolver.resolve("file:///tmp/page.html", "<link rel=icon href=/favicon.ico>"))
    }
}
