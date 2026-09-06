package net.matsudamper.mastodon.rss.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HttpUrlTest {
    @Test
    fun `http と https だけ通す`() {
        assertEquals("https://example.com/", HttpUrl.sanitize("https://example.com/"))
        assertEquals("http://example.com/", HttpUrl.sanitize("http://example.com/"))
    }

    @Test
    fun `javascript などのスキームは拒否する`() {
        assertNull(HttpUrl.sanitize("javascript:alert(1)"))
        assertNull(HttpUrl.sanitize("data:text/plain,hello"))
    }

    @Test
    fun `基準 URL から相対 URL を解決する`() {
        assertEquals(
            "https://example.com/blog",
            HttpUrl.sanitize("/blog", baseUrl = "https://example.com/feed.xml"),
        )
    }

    @Test
    fun `解決後に危険なスキームになるものは拒否する`() {
        assertNull(HttpUrl.sanitize("javascript:alert(1)", baseUrl = "https://example.com/feed.xml"))
    }
}
