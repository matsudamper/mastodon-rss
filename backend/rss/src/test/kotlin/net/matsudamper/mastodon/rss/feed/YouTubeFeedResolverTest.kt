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
    private val channelPage = YouTubeFeedSource.NeedsPageLookup.Page.CHANNEL
    private val videoPage = YouTubeFeedSource.NeedsPageLookup.Page.VIDEO

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

        assertEquals(channelLookup("https://www.youtube.com/@LinusTechTips"), source)
    }

    @Test
    fun `ハンドルの後ろにタブが付いていても落とす`() {
        val source = YouTubeFeedResolver.resolve("https://www.youtube.com/@LinusTechTips/videos")

        assertEquals(channelLookup("https://www.youtube.com/@LinusTechTips"), source)
    }

    @Test
    fun `旧ユーザー名は綴りをそのまま使わずページを引く`() {
        // ?user=<名前> のフィードは、同じ綴りの別チャンネルを 200 で返すことがある
        val source = YouTubeFeedResolver.resolve("https://www.youtube.com/user/LinusTechTips")

        assertEquals(channelLookup("https://www.youtube.com/user/LinusTechTips"), source)
    }

    @Test
    fun `旧ユーザー名のフィード URL を貼られてもページを引き直す`() {
        val source = YouTubeFeedResolver.resolve("https://www.youtube.com/feeds/videos.xml?user=LinusTechTips")

        assertEquals(channelLookup("https://www.youtube.com/user/LinusTechTips"), source)
    }

    @Test
    fun `カスタム URL もページを引く`() {
        val source = YouTubeFeedResolver.resolve("https://www.youtube.com/c/LinusTechTips")

        assertEquals(channelLookup("https://www.youtube.com/c/LinusTechTips"), source)
    }

    @Test
    fun `動画の URL は動画のページを引く`() {
        val source = YouTubeFeedResolver.resolve("https://www.youtube.com/watch?v=XiSMWonFuQQ")

        assertEquals(videoLookup("https://www.youtube.com/watch?v=XiSMWonFuQQ"), source)
    }

    @Test
    fun `再生リストの中の動画は動画として扱う`() {
        // list が付いていても、貼った人が見ていたのは v の動画
        val source = YouTubeFeedResolver.resolve("https://www.youtube.com/watch?v=XiSMWonFuQQ&list=PLIivdWyY5sqIij")

        assertEquals(videoLookup("https://www.youtube.com/watch?v=XiSMWonFuQQ"), source)
    }

    @Test
    fun `v が無く list だけなら再生リストとして扱う`() {
        val source = YouTubeFeedResolver.resolve("https://www.youtube.com/watch?list=PLIivdWyY5sqIij")

        assertEquals(YouTubeFeedSource.Kind.PLAYLIST, (source as YouTubeFeedSource.Feed).kind)
    }

    @Test
    fun `短縮 URL と shorts と埋め込みも動画として扱う`() {
        val expected = videoLookup("https://www.youtube.com/watch?v=XiSMWonFuQQ")

        assertEquals(expected, YouTubeFeedResolver.resolve("https://youtu.be/XiSMWonFuQQ"))
        assertEquals(expected, YouTubeFeedResolver.resolve("https://youtu.be/XiSMWonFuQQ?si=abcdef"))
        assertEquals(expected, YouTubeFeedResolver.resolve("https://www.youtube.com/shorts/XiSMWonFuQQ"))
        assertEquals(expected, YouTubeFeedResolver.resolve("https://www.youtube.com/live/XiSMWonFuQQ"))
        assertEquals(expected, YouTubeFeedResolver.resolve("https://www.youtube-nocookie.com/embed/XiSMWonFuQQ"))
    }

    @Test
    fun `埋め込みの再生リストは動画 ID と間違えない`() {
        // videoseries はちょうど 11 文字で、動画 ID の形に通ってしまう
        val source = YouTubeFeedResolver.resolve("https://www.youtube.com/embed/videoseries?list=$playlistId")

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
    fun `埋め込みの配信はチャンネルとして読む`() {
        // live_stream もちょうど 11 文字。チャンネルはクエリに入っている
        val source = YouTubeFeedResolver.resolve("https://www.youtube.com/embed/live_stream?channel=$channelId")

        assertEquals(channelFeed, (source as YouTubeFeedSource.Feed).url)
    }

    @Test
    fun `旧い共有リンクは中の URL を読み直す`() {
        val source =
            YouTubeFeedResolver.resolve(
                "https://www.youtube.com/attribution_link?a=abc&u=%2Fwatch%3Fv%3DXiSMWonFuQQ%26feature%3Dshare",
            )

        assertEquals(videoLookup("https://www.youtube.com/watch?v=XiSMWonFuQQ"), source)
    }

    @Test
    fun `入れ子の共有リンクは途中で諦める`() {
        // u に共有リンクを入れ子にすると、入力の長さの分だけ再帰する
        val nested = "/attribution_link?u=".repeat(10) + "/watch?v=XiSMWonFuQQ"

        assertNull(YouTubeFeedResolver.resolve("https://www.youtube.com/attribution_link?u=$nested"))
    }

    @Test
    fun `名前だけのカスタム URL もページを引く`() {
        // /c/ を挟まない旧来の形
        assertEquals(
            channelLookup("https://www.youtube.com/LinusTechTips"),
            YouTubeFeedResolver.resolve("https://www.youtube.com/LinusTechTips"),
        )
        assertEquals(
            channelLookup("https://www.youtube.com/LinusTechTips"),
            YouTubeFeedResolver.resolve("https://www.youtube.com/LinusTechTips/videos"),
        )
    }

    @Test
    fun `YouTube 自身のページは名前として読まない`() {
        assertNull(YouTubeFeedResolver.resolve("https://www.youtube.com/feed/subscriptions"))
        assertNull(YouTubeFeedResolver.resolve("https://www.youtube.com/account"))
        assertNull(YouTubeFeedResolver.resolve("https://www.youtube.com/watch_videos?video_ids=XiSMWonFuQQ"))
    }

    @Test
    fun `スマホと音楽とゲームのホストも同じ扱いにする`() {
        assertEquals(channelFeed, feedUrlOf("https://m.youtube.com/channel/$channelId"))
        assertEquals(channelFeed, feedUrlOf("https://music.youtube.com/channel/$channelId"))
        assertEquals(channelFeed, feedUrlOf("https://gaming.youtube.com/channel/$channelId"))
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

        assertEquals(channelId, YouTubeFeedResolver.channelIdFromPageHtml(channelPage, html))
    }

    @Test
    fun `canonical からチャンネル ID を拾う`() {
        val html = """<link rel="canonical" href="https://www.youtube.com/channel/$channelId">"""

        assertEquals(channelId, YouTubeFeedResolver.channelIdFromPageHtml(channelPage, html))
    }

    @Test
    fun `動画のページは埋め込まれた JSON からチャンネル ID を拾う`() {
        assertEquals(channelId, YouTubeFeedResolver.channelIdFromPageHtml(videoPage, """{"externalId":"$channelId"}"""))
        assertEquals(channelId, YouTubeFeedResolver.channelIdFromPageHtml(videoPage, """{"channelId":"$channelId"}"""))
    }

    @Test
    fun `名乗っていないタグのチャンネルは拾わない`() {
        // ページには他のチャンネルへのリンクも並んでいる。
        // 先にあるものを名乗りと取り違えると、別のチャンネルを購読することになる
        val other = "UCaaaaaaaaaaaaaaaaaaaaaa"
        val html =
            """<a href="https://www.youtube.com/channel/$other">別のチャンネル</a>""" +
                """<meta property="og:image" content="https://www.youtube.com/channel/$other/icon.jpg">""" +
                """<link rel="canonical" href="https://www.youtube.com/channel/$channelId">"""

        assertEquals(channelId, YouTubeFeedResolver.channelIdFromPageHtml(channelPage, html))
    }

    @Test
    fun `JSON の中の URL は名乗りとして扱わない`() {
        // JSON の中では " が \" になっていて、属性としては読めない
        val json = """{"url":"https:\/\/www.youtube.com\/channel\/$channelId"}"""

        assertNull(YouTubeFeedResolver.channelIdFromPageHtml(channelPage, json))
    }

    @Test
    fun `チャンネルのページは自分で名乗っていない ID を拾わない`() {
        // 一覧のページには並んでいる動画の投稿者の channelId も入っている。
        // 拾うと、貼られたものと関係の無いチャンネルを購読することになる
        assertNull(YouTubeFeedResolver.channelIdFromPageHtml(channelPage, """{"channelId":"$channelId"}"""))
    }

    @Test
    fun `チャンネル ID が無ければ拾えない`() {
        assertNull(YouTubeFeedResolver.channelIdFromPageHtml(channelPage, "<html><body>同意画面</body></html>"))
        assertNull(YouTubeFeedResolver.channelIdFromPageHtml(channelPage, ""))
    }

    @Test
    fun `チャンネルのページの JSON から説明文を拾う`() {
        val html =
            """{"metadata":{"channelMetadataRenderer":{"title":"配信者",""" +
                """"description":"1 行目\n2 行目 \u0026 3 行目","channelUrl":"https://www.youtube.com/channel/$channelId"}}}"""

        assertEquals("1 行目\n2 行目 & 3 行目", YouTubeFeedResolver.channelDescriptionFromPageHtml(html))
    }

    @Test
    fun `チャンネルのページ以外からは説明文を拾わない`() {
        // 動画のページの説明文をチャンネルの説明文として拾ってしまわないこと
        assertNull(YouTubeFeedResolver.channelDescriptionFromPageHtml("""{"videoDetails":{"shortDescription":"動画の説明"}}"""))
        assertNull(YouTubeFeedResolver.channelDescriptionFromPageHtml(""))
    }

    @Test
    fun `説明文が空なら拾わない`() {
        val html = """{"channelMetadataRenderer":{"title":"配信者","description":""}}"""

        assertNull(YouTubeFeedResolver.channelDescriptionFromPageHtml(html))
    }

    @Test
    fun `チャンネルのページの og image からアイコンを拾う`() {
        val html =
            """<html><head><meta property="og:title" content="配信者">""" +
                """<meta property="og:image" content="https://yt3.googleusercontent.com/avatar=s900-c-k">""" +
                """</head></html>"""

        assertEquals("https://yt3.googleusercontent.com/avatar=s900-c-k", YouTubeFeedResolver.channelIconFromPageHtml(html))
    }

    @Test
    fun `og image が無ければアイコンを拾わない`() {
        // JSON の中に URL が並んでいても、タグとして名乗っていないものは拾わない
        assertNull(YouTubeFeedResolver.channelIconFromPageHtml("""{"avatar":{"thumbnails":[{"url":"https://example.com/a.png"}]}}"""))
        assertNull(YouTubeFeedResolver.channelIconFromPageHtml(""))
    }

    @Test
    fun `チャンネル ID からチャンネルのページの URL を作る`() {
        assertEquals("https://www.youtube.com/channel/$channelId", YouTubeFeedResolver.channelPageUrl(channelId))
        assertNull(YouTubeFeedResolver.channelPageUrl("UC123"))
    }

    private fun feedUrlOf(input: String): String? = (YouTubeFeedResolver.resolve(input) as? YouTubeFeedSource.Feed)?.url

    private fun channelLookup(pageUrl: String): YouTubeFeedSource.NeedsPageLookup =
        YouTubeFeedSource.NeedsPageLookup(pageUrl, YouTubeFeedSource.NeedsPageLookup.Page.CHANNEL)

    private fun videoLookup(pageUrl: String): YouTubeFeedSource.NeedsPageLookup =
        YouTubeFeedSource.NeedsPageLookup(pageUrl, YouTubeFeedSource.NeedsPageLookup.Page.VIDEO)
}
