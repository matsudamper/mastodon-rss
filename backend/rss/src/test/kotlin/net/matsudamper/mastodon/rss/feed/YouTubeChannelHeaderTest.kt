package net.matsudamper.mastodon.rss.feed

import kotlin.test.Test
import kotlin.test.assertEquals

class YouTubeChannelHeaderTest {
    @Test
    fun `現在のバナー形式を読む`() {
        val html = """
            <script>
            {"pageHeaderViewModel":{"banner":{"imageBannerViewModel":{"image":{"sources":[
              {"url":"https://yt3.googleusercontent.com/banner-small"},
              {"url":"https://yt3.googleusercontent.com/banner-large\u0026x=1"}
            ]}}}}}
            </script>
        """.trimIndent()

        assertEquals(
            "https://yt3.googleusercontent.com/banner-large&x=1",
            YouTubeChannelHeader.fromPageHtml(html),
        )
    }

    @Test
    fun `プリロード対象一覧に含まれる名前だけの imageBannerViewModel を実データと誤認しない`() {
        val html = """
            <script>
            {"preloadMessageNames":["pageHeaderViewModel","imageBannerViewModel","dynamicTextViewModel"]}
            </script>
            <script>
            {"lockupViewModel":{"contentImage":{"thumbnailViewModel":{"image":{"sources":[
              {"url":"https://i.ytimg.com/vi/unrelated-thumbnail.jpg"}
            ]}}}}}
            </script>
            <script>
            {"pageHeaderViewModel":{"banner":{"imageBannerViewModel":{"image":{"sources":[
              {"url":"https://yt3.googleusercontent.com/banner-large"}
            ]}}}}}
            </script>
        """.trimIndent()

        assertEquals(
            "https://yt3.googleusercontent.com/banner-large",
            YouTubeChannelHeader.fromPageHtml(html),
        )
    }

    @Test
    fun `旧バナー形式を読む`() {
        val html = """
            <script>
            {"c4TabbedHeaderRenderer":{"banner":{"thumbnails":[
              {"url":"https:\/\/yt3.googleusercontent.com\/banner"}
            ]}}}
            </script>
        """.trimIndent()

        assertEquals(
            "https://yt3.googleusercontent.com/banner",
            YouTubeChannelHeader.fromPageHtml(html),
        )
    }
}
