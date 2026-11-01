package net.matsudamper.mastodon.rss.webpage

import kotlin.test.Test
import kotlin.test.assertFalse

class PublicPageResponseTest {
    @Test
    fun `HTMLのqualityがゼロでも優先されるワイルドカードは本家と同じくHTMLを選ぶ`() {
        assertFalse(PublicPageResponse.wantsJson("*/*;q=0.9, text/html;q=0, application/activity+json;q=0.8"))
    }
}
