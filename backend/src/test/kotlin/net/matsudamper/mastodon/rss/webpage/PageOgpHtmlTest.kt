package net.matsudamper.mastodon.rss.webpage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PageOgpHtmlTest {
    private val indexHtml = "<html><head>\n<title>mastodon-rss</title>\n</head><body><script src=\"/frontend.js\"></script></body></html>"

    @Test
    fun `head の終わりにカードの meta を足し、値の記号はエスケープする`() {
        val ogp = PageOgp(
            type = PageOgpType.Article,
            title = "名前 \$1 (@feed@example.com)",
            description = "\"><script>alert(1)</script>",
            url = "https://example.com/@feed/n1",
            imageUrl = "https://example.com/users/feed/icon?v=1&a=b",
        )

        val html = PageOgpHtml.inject(indexHtml = indexHtml, ogp = ogp)

        assertTrue(html.contains("""<meta property="og:description" content="&quot;&gt;&lt;script&gt;alert(1)&lt;/script&gt;">"""))
        assertTrue(html.contains("""<meta property="og:image" content="https://example.com/users/feed/icon?v=1&amp;a=b">"""))
        assertTrue(html.contains("<title>名前 \$1 (@feed@example.com)</title>"))
        assertFalse(html.contains("<title>mastodon-rss</title>"))
        assertTrue(html.indexOf("og:title") < html.indexOf("</head>"))
        assertEquals(1, Regex("<script").findAll(html).count())
    }

    @Test
    fun `説明と画像が無ければその meta を出さない`() {
        val ogp = PageOgp(type = PageOgpType.Profile, title = "feed", description = null, url = "https://example.com/@feed", imageUrl = null)

        val html = PageOgpHtml.inject(indexHtml = indexHtml, ogp = ogp)

        assertFalse(html.contains("og:description"))
        assertFalse(html.contains("og:image"))
        assertTrue(html.contains("""<meta name="twitter:card" content="summary">"""))
    }
}
