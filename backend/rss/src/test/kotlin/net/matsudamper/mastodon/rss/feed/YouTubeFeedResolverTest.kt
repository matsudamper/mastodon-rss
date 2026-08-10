package net.matsudamper.mastodon.rss.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// 貼られた URL をフィードの URL に直せることを確認する。
// 期待値の URL の形と、ページから ID を抜くときの手掛かりは、
// 実際に YouTube が返したものに合わせてある。
class YouTubeFeedResolverTest {
    private val channelId = "UCXuqSBlHAE6Xw-yeJA0Tunw"
    private val channelFeed = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
    private val playlistId = "PLIivdWyY5sqIij_cgINUHZDMnGjVx3rxi"

    @Test
    fun `チャンネルの URL からフィードを作る`() {
        val source = YouTubeFeedResolver.resolve("https://www.youtube.com/channel/$channelId")

        assertEquals(
            YouTubeFeedSource.Feed(
                url = channelFeed,
                kind = YouTubeFeedSource.Kind.CHANNEL,
                id = channelId,
            ),
            source,
        )
    }

    @Test
    fun `チャンネルの中のタブが付いていても読める`() {
        val source = YouTubeFeedResolver.resolve("https://www.youtube.com/channel/$channelId/streams")

        assertEquals(channelFeed, (source as YouTubeFeedSource.Feed).url)
    }

    @Test
    fun `フィードの URL はそのまま通す`() {
        val source = YouTubeFeedResolver.resolve(channelFeed)

        assertEquals(channelFeed, (source as YouTubeFeedSource.Feed).url)
    }

    @Test
    fun `再生リストの URL からフィードを作る`() {
        val source = YouTubeFeedResolver.resolve("https://www.youtube.com/playlist?list=$playlistId")

        assertEquals(
            YouTubeFeedSource.Feed(
                url = "https://www.youtube.com/feeds/videos.xml?playlist_id=$playlistId",
                kind = YouTubeFeedSource.Kind.PLAYLIST,
                id = playlistId,
            ),
            source,
        )
    }

    @Test
    fun `その場で作られるミックスは登録させない`() {
        // RD で始まる再生リストのフィードは 404 になる
        assertNull(YouTubeFeedResolver.resolve("https://www.youtube.com/playlist?list=RDXiSMWonFuQQ"))
    }

    @Test
    fun `後で見ると高く評価した動画は登録させない`() {
        assertNull(YouTubeFeedResolver.resolve("https://www.youtube.com/playlist?list=WL"))
        assertNull(YouTubeFeedResolver.resolve("https://www.youtube.com/playlist?list=LL"))
    }

    @Test
    fun `ハンドルはページを引かないと分からない`() {
        val source = YouTubeFeedResolver.resolve("https://www.youtube.com/@LinusTechTips")

        assertEquals(YouTubeFeedSource.NeedsPageLookup("https://www.youtube.com/@LinusTechTips"), source)
    }

    @Test
    fun `ハンドルの後ろにタブが付いていても落とす`() {
        val source = YouTubeFeedResolver.resolve("https://www.youtube.com/@LinusTechTips/videos")

        assertEquals(YouTubeFeedSource.NeedsPageLookup("https://www.youtube.com/@LinusTechTips"), source)
    }

    @Test
    fun `旧ユーザー名は綴りをそのまま使わずページを引く`() {
        // ?user=<名前> のフィードは、同じ綴りの別チャンネルを 200 で返すことがある
        val source = YouTubeFeedResolver.resolve("https://www.youtube.com/user/LinusTechTips")

        assertEquals(YouTubeFeedSource.NeedsPageLookup("https://www.youtube.com/user/LinusTechTips"), source)
    }

    @Test
    fun `旧ユーザー名のフィード URL を貼られてもページを引き直す`() {
        val source = YouTubeFeedResolver.resolve("https://www.youtube.com/feeds/videos.xml?user=LinusTechTips")

        assertEquals(YouTubeFeedSource.NeedsPageLookup("https://www.youtube.com/user/LinusTechTips"), source)
    }

    @Test
    fun `カスタム URL もページを引く`() {
        val source = YouTubeFeedResolver.resolve("https://www.youtube.com/c/LinusTechTips")

        assertEquals(YouTubeFeedSource.NeedsPageLookup("https://www.youtube.com/c/LinusTechTips"), source)
    }

    @Test
    fun `動画の URL は動画のページを引く`() {
        val source = YouTubeFeedResolver.resolve("https://www.youtube.com/watch?v=XiSMWonFuQQ")

        assertEquals(YouTubeFeedSource.NeedsPageLookup("https://www.youtube.com/watch?v=XiSMWonFuQQ"), source)
    }

    @Test
    fun `再生リストの中の動画は動画として扱う`() {
        // list が付いていても、貼った人が見ていたのは v の動画
        val source = YouTubeFeedResolver.resolve("https://www.youtube.com/watch?v=XiSMWonFuQQ&list=PLIivdWyY5sqIij")

        assertEquals(YouTubeFeedSource.NeedsPageLookup("https://www.youtube.com/watch?v=XiSMWonFuQQ"), source)
    }

    @Test
    fun `v が無く list だけなら再生リストとして扱う`() {
        val source = YouTubeFeedResolver.resolve("https://www.youtube.com/watch?list=PLIivdWyY5sqIij")

        assertEquals(YouTubeFeedSource.Kind.PLAYLIST, (source as YouTubeFeedSource.Feed).kind)
    }

    @Test
    fun `短縮 URL と shorts と埋め込みも動画として扱う`() {
        val expected = YouTubeFeedSource.NeedsPageLookup("https://www.youtube.com/watch?v=XiSMWonFuQQ")

        assertEquals(expected, YouTubeFeedResolver.resolve("https://youtu.be/XiSMWonFuQQ"))
        assertEquals(expected, YouTubeFeedResolver.resolve("https://youtu.be/XiSMWonFuQQ?si=abcdef"))
        assertEquals(expected, YouTubeFeedResolver.resolve("https://www.youtube.com/shorts/XiSMWonFuQQ"))
        assertEquals(expected, YouTubeFeedResolver.resolve("https://www.youtube.com/live/XiSMWonFuQQ"))
        assertEquals(expected, YouTubeFeedResolver.resolve("https://www.youtube-nocookie.com/embed/XiSMWonFuQQ"))
    }

    @Test
    fun `スマホと音楽のホストも同じ扱いにする`() {
        assertEquals(channelFeed, feedUrlOf("https://m.youtube.com/channel/$channelId"))
        assertEquals(channelFeed, feedUrlOf("https://music.youtube.com/channel/$channelId"))
    }

    @Test
    fun `スキームが欠けていても読む`() {
        // 選択してコピーすると頭が落ちることがある
        assertEquals(channelFeed, feedUrlOf("www.youtube.com/channel/$channelId"))
        assertEquals(channelFeed, feedUrlOf("youtube.com/channel/$channelId"))
        assertEquals(channelFeed, feedUrlOf("//youtube.com/channel/$channelId"))
    }

    @Test
    fun `前後の空白は落とす`() {
        assertEquals(channelFeed, feedUrlOf("  https://www.youtube.com/channel/$channelId  "))
    }

    @Test
    fun `YouTube でない URL は分からない`() {
        assertNull(YouTubeFeedResolver.resolve("https://example.com/channel/$channelId"))
        assertNull(YouTubeFeedResolver.resolve("https://youtube.com.example.com/channel/$channelId"))
    }

    @Test
    fun `http と https 以外は受け取らない`() {
        assertNull(YouTubeFeedResolver.resolve("javascript:alert(1)"))
        assertNull(YouTubeFeedResolver.resolve("ftp://www.youtube.com/channel/$channelId"))
    }

    @Test
    fun `フィードに繋がらない YouTube の URL は分からない`() {
        assertNull(YouTubeFeedResolver.resolve("https://www.youtube.com/"))
        assertNull(YouTubeFeedResolver.resolve("https://www.youtube.com/results?search_query=rss"))
        assertNull(YouTubeFeedResolver.resolve("https://www.youtube.com/channel/not-a-channel-id"))
        assertNull(YouTubeFeedResolver.resolve("https://www.youtube.com/watch?v=short"))
        assertNull(YouTubeFeedResolver.resolve(""))
    }

    @Test
    fun `チャンネル ID とフィードの URL を相互に作れる`() {
        assertEquals(channelFeed, YouTubeFeedResolver.feedUrlForChannel(channelId))
        assertNull(YouTubeFeedResolver.feedUrlForChannel("UC"))
        assertEquals(
            "https://www.youtube.com/feeds/videos.xml?playlist_id=PLabc",
            YouTubeFeedResolver.feedUrlForPlaylist("PLabc"),
        )
        assertNull(YouTubeFeedResolver.feedUrlForPlaylist("RDabc"))
    }

    @Test
    fun `ページが名乗っているフィードの URL からチャンネル ID を拾う`() {
        val html =
            """<link rel="alternate" type="application/rss+xml" title="RSS" """ +
                """href="https://www.youtube.com/feeds/videos.xml?channel_id=$channelId">"""

        assertEquals(channelId, YouTubeFeedResolver.channelIdFromPageHtml(html))
    }

    @Test
    fun `canonical からチャンネル ID を拾う`() {
        val html = """<link rel="canonical" href="https://www.youtube.com/channel/$channelId">"""

        assertEquals(channelId, YouTubeFeedResolver.channelIdFromPageHtml(html))
    }

    @Test
    fun `埋め込まれた JSON からチャンネル ID を拾う`() {
        assertEquals(channelId, YouTubeFeedResolver.channelIdFromPageHtml("""{"externalId":"$channelId"}"""))
        assertEquals(channelId, YouTubeFeedResolver.channelIdFromPageHtml("""{"channelId":"$channelId"}"""))
        // JSON の中の URL はエスケープされていることがある
        assertEquals(
            channelId,
            YouTubeFeedResolver.channelIdFromPageHtml("""{"url":"https:\/\/www.youtube.com\/channel\/$channelId"}"""),
        )
    }

    @Test
    fun `チャンネル ID が無ければ拾えない`() {
        assertNull(YouTubeFeedResolver.channelIdFromPageHtml("<html><body>同意画面</body></html>"))
        assertNull(YouTubeFeedResolver.channelIdFromPageHtml(""))
    }

    private fun feedUrlOf(input: String): String? = (YouTubeFeedResolver.resolve(input) as? YouTubeFeedSource.Feed)?.url
}
